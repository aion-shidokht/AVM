package org.aion.avm.rt;

import org.aion.avm.arraywrapper.ByteArray;


/**
 * The address has a very specific meaning, within the environment, so we wrap a ByteArray to produce this more specific type.
 * 
 * This is likely to change a lot as we build more DApp tests (see issue-76 for more details on how we might want to evolve this).
 * There is a good chance that we will convert this into an interface so that our implementation can provide a richer interface to
 * our AVM code than we want to support for the contract.
 */
public class Address extends org.aion.avm.java.lang.Object {
    // Runtime-facing implementation.
    public static final int avm_LENGTH = 32;

    private final ByteArray underlying;

    /**
     * The constructor which user code can call, directly, to create an Address object.
     * This will remain until/unless we decide to make a factory which creates these from within the runtime.
     * 
     * @param raw The raw bytes representing the address.
     */
    public Address(ByteArray raw) {
        if (raw == null || raw.length() != avm_LENGTH) {
            throw new IllegalArgumentException();
        }

        this.underlying = raw;
    }

    /**
     * Similarly, this method will probably be removed or otherwise hidden.
     * 
     * @return The raw bytes underneath the address.
     */
    public ByteArray avm_unwrap() {
        return this.underlying;
    }

    // Compiler-facing implementation.
    public static final int LENGTH = avm_LENGTH;

    /**
     * Note that this constructor is only here to support our tests while we decide whether or not to expose the constructor
     * of construct the class this way.
     * 
     * @param raw The raw bytes representing the address.
     */
    public Address(byte[] raw) {
        this(new ByteArray(raw));
    }

    /**
     * Similarly, this method will probably be removed or otherwise hidden.
     * 
     * @return The raw bytes underneath the address.
     */
    public byte[] unwrap() {
        return this.underlying.getUnderlying();
    }
}
