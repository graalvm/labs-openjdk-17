/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#include "precompiled.hpp"
#include "code/codeCache.hpp"
#include "code/nativeInst.hpp"
#include "gc/shared/barrierSetNMethod.hpp"
#include "logging/log.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/registerMap.hpp"
#include "runtime/thread.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#if INCLUDE_JVMCI
#include "jvmci/jvmciRuntime.hpp"
#endif

class NativeNMethodBarrier {
  // This is the offset of the entry barrier relative to where the frame is completed.
  // If any code changes between the end of the verified entry where the entry
  // barrier resides, and the completion of the frame, then
  // NativeNMethodBarrier::verify() will immediately complain when it does
  // not find the expected native instruction at this offset, which needs updating.
  // Note that this offset is invariant of PreserveFramePointer.

  static const int entry_barrier_offset = -4 * 11;

  address  _instruction_address;
  int*     _guard_addr;
  nmethod* _nm;

  address instruction_address() const { return _instruction_address; }

  int *guard_addr() {
    return _guard_addr;
  }

public:
  NativeNMethodBarrier(nmethod* nm): _nm(nm) {
    address barrier_address;
#if INCLUDE_JVMCI
    if (nm->is_compiled_by_jvmci()) {
      address pc = nm->code_begin() + nm->jvmci_nmethod_data()->nmethod_entry_patch_offset();
      RelocIterator iter(nm, pc, pc + 4);
      guarantee(iter.next(), "missing relocs");
      guarantee(iter.type() == relocInfo::section_word_type, "unexpected reloc");

      _guard_addr = (int*) iter.section_word_reloc()->target();
      _instruction_address = pc;
    } else
#endif
      {
        _instruction_address = nm->code_begin() + nm->frame_complete_offset() + entry_barrier_offset;
        _guard_addr = reinterpret_cast<int*>(_instruction_address + 10 * 4);
      }
  }


  int get_value() {
    return Atomic::load_acquire(guard_addr());
  }

  void set_value(int value) {
    Atomic::release_store(guard_addr(), value);
  }

  bool check_barrier(FormatBuffer<>& msg) const;
  void verify() const {
    FormatBuffer<> msg("%s", "");
    assert(check_barrier(msg), "%s", msg.buffer());
  }
};

// Store the instruction bitmask, bits and name for checking the barrier.
struct CheckInsn {
  uint32_t mask;
  uint32_t bits;
  const char *name;
};

static const struct CheckInsn barrierInsn[] = {
  { 0xff000000, 0x18000000, "ldr (literal)" },
  { 0xfffff0ff, 0xd50330bf, "dmb" },
  { 0xffc00000, 0xb9400000, "ldr"},
  { 0x7f20001f, 0x6b00001f, "cmp"},
  { 0xff00001f, 0x54000000, "b.eq"},
  { 0xff800000, 0xd2800000, "mov"},
  { 0xff800000, 0xf2800000, "movk"},
  { 0xff800000, 0xf2800000, "movk"},
  { 0xfffffc1f, 0xd63f0000, "blr"},
  { 0xfc000000, 0x14000000, "b"}
};

// The encodings must match the instructions emitted by
// BarrierSetAssembler::nmethod_entry_barrier. The matching ignores the specific
// register numbers and immediate values in the encoding.
bool NativeNMethodBarrier::check_barrier(FormatBuffer<>& msg) const {
  intptr_t addr = (intptr_t) instruction_address();
  unsigned int to_check = sizeof(barrierInsn)/sizeof(struct CheckInsn);
#if INCLUDE_JVMCI
  if (_nm->is_compiled_by_jvmci()) {
    // the first four instructions are inline
    to_check = 4;
  }
#endif
  for(unsigned int i = 0; i < to_check; i++ ) {
    uint32_t inst = *((uint32_t*) addr);
    if ((inst & barrierInsn[i].mask) != barrierInsn[i].bits) {
      msg.print("Addr: " INTPTR_FORMAT " Code: 0x%x %s", addr, inst, barrierInsn[i].name);
      return false;
    }
    addr +=4;
  }
  return true;
}


/* We're called from an nmethod when we need to deoptimize it. We do
   this by throwing away the nmethod's frame and jumping to the
   ic_miss stub. This looks like there has been an IC miss at the
   entry of the nmethod, so we resolve the call, which will fall back
   to the interpreter if the nmethod has been unloaded. */
void BarrierSetNMethod::deoptimize(nmethod* nm, address* return_address_ptr) {

  typedef struct {
    intptr_t *sp; intptr_t *fp; address lr; address pc;
  } frame_pointers_t;

  frame_pointers_t *new_frame = (frame_pointers_t *)(return_address_ptr - 5);

  JavaThread *thread = JavaThread::current();
  RegisterMap reg_map(thread, false);
  frame frame = thread->last_frame();

  assert(frame.is_compiled_frame() || frame.is_native_frame(), "must be");
  assert(frame.cb() == nm, "must be");
  frame = frame.sender(&reg_map);

  LogTarget(Trace, nmethod, barrier) out;
  if (out.is_enabled()) {
    ResourceMark mark;
    log_trace(nmethod, barrier)("deoptimize(nmethod: %s(%p), return_addr: %p, osr: %d, thread: %p(%s), making rsp: %p) -> %p",
                                nm->method()->name_and_sig_as_C_string(),
                                nm, *(address *) return_address_ptr, nm->is_osr_method(), thread,
                                thread->get_thread_name(), frame.sp(), nm->verified_entry_point());
  }

  new_frame->sp = frame.sp();
  new_frame->fp = frame.fp();
  new_frame->lr = frame.pc();
  new_frame->pc = SharedRuntime::get_handle_wrong_method_stub();
}

void BarrierSetNMethod::disarm(nmethod* nm) {
  if (!supports_entry_barrier(nm)) {
    return;
  }

  // Disarms the nmethod guard emitted by BarrierSetAssembler::nmethod_entry_barrier.
  // Symmetric "LDR; DMB ISHLD" is in the nmethod barrier.
  NativeNMethodBarrier barrier(nm);
  barrier.set_value(disarmed_value());
}

bool BarrierSetNMethod::is_armed(nmethod* nm) {
  if (!supports_entry_barrier(nm)) {
    return false;
  }

  NativeNMethodBarrier barrier(nm);
  return barrier.get_value() != disarmed_value();
}


#if INCLUDE_JVMCI
bool BarrierSetNMethod::verify_barrier(nmethod* nm, FormatBuffer<>& msg) {
  NativeNMethodBarrier barrier(nm);
  return barrier.check_barrier(msg);
}
#endif
