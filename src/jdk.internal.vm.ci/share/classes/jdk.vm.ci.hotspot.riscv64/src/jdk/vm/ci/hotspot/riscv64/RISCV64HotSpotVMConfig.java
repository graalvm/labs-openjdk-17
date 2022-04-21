/*
 * Copyright (c) 2011, 2019, Oracle and/or its affiliates. All rights reserved.
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
 */
package jdk.vm.ci.hotspot.riscv64;

import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;
import jdk.vm.ci.services.Services;

public class RISCV64HotSpotVMConfig extends HotSpotVMConfigAccess {

    RISCV64HotSpotVMConfig(HotSpotVMConfigStore config) {
        super(config);
    }

    final boolean linuxOs = Services.getSavedProperty("os.name", "").startsWith("Linux");

    final boolean useCompressedOops = getFlag("UseCompressedOops", Boolean.class);

    // CPU Capabilities

    /*
     * These flags are set based on the corresponding command line flags.
     */
    final boolean useConservativeFence = getFlag("UseConservativeFence", Boolean.class);
    final boolean useVExt = getFlag("UseVExt", Boolean.class);
    final boolean avoidUnalignedAccesses = getFlag("AvoidUnalignedAccesses", Boolean.class);

    /*
     * These flags are set if the corresponding support is in the hardware.
     */
    // Checkstyle: stop
    // Riscv64 VM_Version is not yet implemented, thus we cannot get the values of the flags
    // Checkstyle: resume

}
