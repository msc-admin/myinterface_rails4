package org.jruby.ext.thread_safe;

import org.jruby.*;
import org.jruby.anno.JRubyClass;
import org.jruby.anno.JRubyMethod;
import org.jruby.ext.thread_safe.jsr166e.ConcurrentHashMapV8;
import org.jruby.runtime.Block;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.runtime.load.Library;

import java.io.IOException;
import java.util.Map;

import static org.jruby.runtime.Visibility.PRIVATE;

/**
 * Native Java implementation to avoid the JI overhead.
 * 
 * @author thedarkone
 */
public class JRubyCacheBackendLibrary implements Library {
    public void load(Ruby runtime, boolean wrap) throws IOException {
        RubyClass jrubyRefClass = runtime.defineClassUnder("JRubyCacheBackend", runtime.getObject(), BACKEND_ALLOCATOR, runtime.getModule("ThreadSafe"));
        jrubyRefClass.setAllocator(BACKEND_ALLOCATOR);
        jrubyRefClass.defineAnnotatedMethods(JRubyCacheBackend.class);
    }
    
    private static final ObjectAllocator BACKEND_ALLOCATOR = new ObjectAllocator() {
        public IRubyObject allocate(Ruby runtime, RubyClass klazz) {
            return new JRubyCacheBackend(runtime, klazz);
        }
    };

    @JRubyClass(name="JRubyCacheBackend", parent="Object")
    public static class JRubyCacheBackend extends RubyObject {
        // Defaults used by the CHM
        static final int DEFAULT_INITIAL_CAPACITY = 16;
        static final float DEFAULT_LOAD_FACTOR = 0.75f;

        private ConcurrentHashMapV8<IRubyObject, IRubyObject> map;

        public JRubyCacheBackend(Ruby runtime, RubyClass klass) {
            super(runtime, klass);
        }

        @JRubyMethod
        public IRubyObject initialize(ThreadContext context) {
            map = new ConcurrentHashMapV8<IRubyObject, IRubyObject>();
            return context.getRuntime().getNil();
        }

        @JRubyMethod
        public IRubyObject initialize(ThreadContext context, IRubyObject options) {
            map = toCHM(context, options);
            return context.getRuntime().getNil();
        }

        private ConcurrentHashMapV8<IRubyObject, IRubyObject> toCHM(ThreadContext context, IRubyObject options) {
            Ruby runtime = context.getRuntime();
            if (!options.isNil() && options.respondsTo("[]")) {
                IRubyObject rInitialCapacity = options.callMethod(context, "[]", runtime.newSymbol("initial_capacity"));
                IRubyObject rLoadFactor      = options.callMethod(context, "[]", runtime.newSymbol("load_factor"));
                int initialCapacity = !rInitialCapacity.isNil() ? RubyNumeric.num2int(rInitialCapacity.convertToInteger()) : DEFAULT_INITIAL_CAPACITY;
                float loadFactor    = !rLoadFactor.isNil() ?      (float)RubyNumeric.num2dbl(rLoadFactor.convertToFloat()) : DEFAULT_LOAD_FACTOR;
                return new ConcurrentHashMapV8<IRubyObject, IRubyObject>(initialCapacity, loadFactor);
            } else {
                return new ConcurrentHashMapV8<IRubyObject, IRubyObject>();
            }
        }

        @JRubyMethod(name = "[]", required = 1)
        public IRubyObject op_aref(ThreadContext context, IRubyObject key) {
            IRubyObject value;
            return ((value = map.get(key)) == null) ? context.getRuntime().getNil() : value;
        }

        @JRubyMethod(name = {"[]="}, required = 2)
        public IRubyObject op_aset(IRubyObject key, IRubyObject value) {
            map.put(key, value);
            return value;
        }

        @JRubyMethod
        public IRubyObject put_if_absent(IRubyObject key, IRubyObject value) {
            IRubyObject result = map.putIfAbsent(key, value);
            return result == null ? getRuntime().getNil() : result;
        }

        @JRubyMethod
        public IRubyObject compute_if_absent(final ThreadContext context, final IRubyObject key, final Block block) {
            return map.computeIfAbsent(key, new ConcurrentHashMapV8.Fun<IRubyObject, IRubyObject>() {
                @Override
                public IRubyObject apply(IRubyObject key) {
                    return block.yieldSpecific(context);
                }
            });
        }

        @JRubyMethod
        public IRubyObject compute_if_present(final ThreadContext context, final IRubyObject key, final Block block) {
            IRubyObject result = map.computeIfPresent(key, new ConcurrentHashMapV8.BiFun<IRubyObject, IRubyObject, IRubyObject>() {
                @Override
                public IRubyObject apply(IRubyObject key, IRubyObject oldValue) {
                    IRubyObject result = block.yieldSpecific(context, oldValue);
                    return result.isNil() ? null : result;
                }
            });
            return result == null ? context.getRuntime().getNil() : result;
        }

        @JRubyMethod
        public IRubyObject compute(final ThreadContext context, final IRubyObject key, final Block block) {
            IRubyObject result = map.compute(key, new ConcurrentHashMapV8.BiFun<IRubyObject, IRubyObject, IRubyObject>() {
                @Override
                public IRubyObject apply(IRubyObject key, IRubyObject oldValue) {
                    IRubyObject result = block.yieldSpecific(context, oldValue);
                    return result.isNil() ? null : result;
                }
            });
            return result == null ? context.getRuntime().getNil() : result;
        }

        @JRubyMethod
        public IRubyObject merge_pair(final ThreadContext context, final IRubyObject key, final IRubyObject value, final Block block) {
            IRubyObject result = map.merge(key, value, new ConcurrentHashMapV8.BiFun<IRubyObject, IRubyObject, IRubyObject>() {
                @Override
                public IRubyObject apply(IRubyObject oldValue, IRubyObject newValue) {
                    IRubyObject result = block.yieldSpecific(context, oldValue);
                    return result.isNil() ? null : result;
                }
            });
            return result == null ? context.getRuntime().getNil() : result;
        }

        @JRubyMethod
        public RubyBoolean replace_pair(IRubyObject key, IRubyObject oldValue, IRubyObject newValue) {
            return getRuntime().newBoolean(map.replace(key, oldValue, newValue));
        }

        @JRubyMethod(name = {"key?"}, required = 1)
        public RubyBoolean has_key_p(IRubyObject key) {
            return map.containsKey(key) ? getRuntime().getTrue() : getRuntime().getFalse();
        }

        @JRubyMethod
        public IRubyObject replace_if_exists(IRubyObject key, IRubyObject value) {
            IRubyObject result = map.replace(key, value);
            return result == null ? getRuntime().getNil() : result;
        }

        @JRubyMethod
        public IRubyObject get_and_set(IRubyObject key, IRubyObject value) {
            IRubyObject result = map.put(key, value);
            return result == null ? getRuntime().getNil() : result;
        }

        @JRubyMethod
        public IRubyObject delete(IRubyObject key) {
            IRubyObject result = map.remove(key);
            return result == null ? getRuntime().getNil() : result;
        }

        @JRubyMethod
        public RubyBoolean delete_pair(IRubyObject key, IRubyObject value) {
            return getRuntime().newBoolean(map.remove(key, value));
        }

        @JRubyMethod
        public IRubyObject clear() {
            map.clear();
            return this;
        }

        @JRubyMethod
         public IRubyObject each_pair(ThreadContext context, Block block) {
            for (Map.Entry<IRubyObject,IRubyObject> entry : map.entrySet()) {
                block.yieldSpecific(context, entry.getKey(), entry.getValue());
            }
            return this;
        }

        @JRubyMethod
        public RubyFixnum size(ThreadContext context) {
            return context.getRuntime().newFixnum(map.size());
        }

        @JRubyMethod
        public IRubyObject get_or_default(IRubyObject key, IRubyObject defaultValue) {
            return map.getValueOrDefault(key, defaultValue);
        }

        @JRubyMethod(visibility = PRIVATE)
        public JRubyCacheBackend initialize_copy(ThreadContext context, IRubyObject other) {
            this.map = new ConcurrentHashMapV8<IRubyObject, IRubyObject>();
            return this;
        }
    }
}
