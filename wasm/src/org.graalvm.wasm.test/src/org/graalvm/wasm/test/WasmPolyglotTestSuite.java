/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.wasm.test;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.wasm.memory.UnsafeWasmMemory;
import org.graalvm.wasm.utils.Assert;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;

import static org.graalvm.wasm.test.WasmTestUtils.hexStringToByteArray;

public class WasmPolyglotTestSuite {
    @Test
    public void testEmpty() throws IOException {
        try (Context context = Context.newBuilder().build()) {
            context.parse(Source.newBuilder("wasm", ByteSequence.create(new byte[0]), "someName").build());
        } catch (PolyglotException pex) {
            Assert.assertTrue("Must be a syntax error.", pex.isSyntaxError());
            Assert.assertTrue("Must not be an internal error.", !pex.isInternalError());
        }
    }

    @Test
    public void test42() throws IOException {
        Context.Builder contextBuilder = Context.newBuilder("wasm");
        Source.Builder sourceBuilder = Source.newBuilder("wasm",
                        ByteSequence.create(binary),
                        "main");
        Source source = sourceBuilder.build();
        Context context = contextBuilder.build();
        context.eval(source);
        Value mainFunction = context.getBindings("wasm").getMember("main");
        Value result = mainFunction.execute();
        Assert.assertEquals("Should be equal: ", 42, result.asInt());
    }

    @Test
    public void unsafeMemoryFreed() throws IOException, NoSuchFieldException, IllegalAccessException {
        Context.Builder contextBuilder = Context.newBuilder("wasm");
        Source.Builder sourceBuilder = Source.newBuilder("wasm",
                        ByteSequence.create(binary),
                        "main");
        Source source = sourceBuilder.build();
        contextBuilder.allowExperimentalOptions(true);
        contextBuilder.option("wasm.UseUnsafeMemory", "true");
        Context context = contextBuilder.build();
        context.eval(source);
        context.getBindings("wasm").getMember("main").execute();
        UnsafeWasmMemory memory = getPrivateField(context.getBindings("wasm").getMember("memory"), "receiver");
        Assert.assertTrue("Memory should have been allocated.", !memory.freed());
        context.close();
        Assert.assertTrue("Memory should have been freed.", memory.freed());

    }

    // (module
    // (type (;0;) (func))
    // (type (;1;) (func (result i32)))
    // (func (;0;) (type 0))
    // (func (;1;) (type 1) (result i32)
    // i32.const 42)
    // (table (;0;) 1 1 funcref)
    // (memory (;0;) 0)
    // (global (;0;) (mut i32) (i32.const 66560))
    // (global (;1;) i32 (i32.const 66560))
    // (global (;2;) i32 (i32.const 1024))
    // (export "main" (func 1))
    // (export "memory" (memory 0))
    // (export "__heap_base" (global 1))
    // (export "__data_end" (global 2)))
    private static final byte[] binary = hexStringToByteArray(
                    "0061736d010000000108026000006000",
                    "017f0303020001040501700101010503",
                    "0100010615037f01418088040b7f0041",
                    "8088040b7f004180080b072c04046d61",
                    "696e0001066d656d6f727902000b5f5f",
                    "686561705f6261736503010a5f5f6461",
                    "74615f656e6403020a090202000b0400",
                    "412a0b");

    /**
     * Do not try this at home.
     */
    @SuppressWarnings("unchecked")
    private static <T> T getPrivateField(Object object, String field) throws NoSuchFieldException, IllegalAccessException {
        Field f = object.getClass().getDeclaredField(field);
        f.setAccessible(true);
        return (T) f.get(object);
    }
}
