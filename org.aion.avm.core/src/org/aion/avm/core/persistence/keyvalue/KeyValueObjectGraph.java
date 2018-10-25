package org.aion.avm.core.persistence.keyvalue;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.aion.avm.core.persistence.ClassNode;
import org.aion.avm.core.persistence.ConstantNode;
import org.aion.avm.core.persistence.ConstructorCache;
import org.aion.avm.core.persistence.SerializedRepresentation;
import org.aion.avm.core.persistence.INode;
import org.aion.avm.core.persistence.IObjectGraphStore;
import org.aion.avm.core.persistence.IRegularNode;
import org.aion.avm.core.persistence.StreamingPrimitiveCodec;
import org.aion.avm.internal.IDeserializer;
import org.aion.avm.internal.IPersistenceToken;
import org.aion.avm.internal.RuntimeAssertionError;
import org.aion.kernel.KernelInterface;


/**
 * The implementation of IObjectGraphStore built directly on top of the KernelInterface (using its key-value store).
 */
public class KeyValueObjectGraph implements IObjectGraphStore {
    private static final long HIGH_RANGE_BIAS = 1_000_000_000L;

    private final Map<Long, KeyValueNode> idToNodeMap;
    private final KernelInterface store;
    private final byte[] address;
    private long nextInstanceId;
    // Tells us which half of the semi-space we are in:  0L or HIGH_RANGE_BIAS.
    // Note that this instanceId is used for segmented addressing, but is not stored in the stored data.
    private long instanceIdBias;
    private int[][] merkleTree;
    private boolean[] dirtyLeaves;

    private ConstructorCache constructorCache;
    private IDeserializer logicalDeserializer;
    private Function<IRegularNode, IPersistenceToken> tokenBuilder;

    public KeyValueObjectGraph(KernelInterface store, byte[] address) {
        this.idToNodeMap = new HashMap<>();
        this.store = store;
        this.address = address;
        
        // We will scoop the nextInstanceId out of our hidden key.
        byte[] rawData = this.store.getStorage(this.address, StorageKeys.INTERNAL_DATA);
        if (null != rawData) {
            StreamingPrimitiveCodec.Decoder decoder = new StreamingPrimitiveCodec.Decoder(rawData);
            this.nextInstanceId = decoder.decodeLong();
            this.instanceIdBias = decoder.decodeLong();
            
            // Read the Merkle tree:  start with the number of levels (we are just using a binary tree).
            // If we proceed with this approach, we should find a way to store this where we don't need to load/store the entire thing on each call.
            int levelCount = decoder.decodeInt();
            this.merkleTree = new int[levelCount][];
            int leafLevelSize = 0;
            for (int i = 0; i < levelCount; ++i) {
                // Read the size of this level.
                int size = decoder.decodeInt();
                this.merkleTree[i] = new int[size];
                
                // Read every element.
                for (int j = 0; j < size; ++j) {
                    this.merkleTree[i][j] = decoder.decodeInt();
                }
                if (0 == i) {
                    leafLevelSize = size;
                }
            }
            this.dirtyLeaves = new boolean[leafLevelSize];
        } else {
            // This must be new.
            this.nextInstanceId = 1L;
            this.instanceIdBias = 0L;
            this.merkleTree = new int[0][];
            this.dirtyLeaves = new boolean[0];
        }
    }

    @Override
    public byte[] getCode() {
        return this.store.getCode(this.address);
    }

    public void setLateComponents(ClassLoader classLoader, IDeserializer logicalDeserializer, Function<IRegularNode, IPersistenceToken> tokenBuilder) {
        this.constructorCache = new ConstructorCache(classLoader);
        this.logicalDeserializer = logicalDeserializer;
        this.tokenBuilder = tokenBuilder;
    }

    @Override
    public byte[] getMetaData() {
        return this.store.getStorage(this.address, StorageKeys.CONTRACT_ENVIRONMENT);
    }

    @Override
    public void setNewMetaData(byte[] data) {
        this.store.putStorage(this.address, StorageKeys.CONTRACT_ENVIRONMENT, data);
    }

    @Override
    public SerializedRepresentation getRoot() {
        // Wipe any stale node state since we are reading the root, again.
        this.idToNodeMap.clear();
        
        byte[] rootBytes = this.store.getStorage(this.address, StorageKeys.CLASS_STATICS);
        RuntimeAssertionError.assertTrue(null != rootBytes);
        return KeyValueCodec.decode(this, rootBytes);
    }

    @Override
    public void setRoot(SerializedRepresentation root) {
        byte[] rootBytes = KeyValueCodec.encode(root);
        RuntimeAssertionError.assertTrue(null != rootBytes);
        this.store.putStorage(this.address, StorageKeys.CLASS_STATICS, rootBytes);
        
        // Update index-0, in the Merkle tree.
        ensureTreeSize(0);
        // Hash the data into the leaf.
        this.merkleTree[0][0] = Arrays.hashCode(rootBytes);
        this.dirtyLeaves[0] = true;
    }

    @Override
    public IRegularNode buildNewRegularNode(String typeName) {
        // Consume the next ID (make sure it doesn't overflow our limit).
        long instanceId = this.nextInstanceId;
        this.nextInstanceId += 1;
        RuntimeAssertionError.assertTrue(this.nextInstanceId < (this.instanceIdBias + HIGH_RANGE_BIAS));
        
        // Create the new node and add it to the map.
        KeyValueNode node = new KeyValueNode(this, typeName, instanceId);
        this.idToNodeMap.put(instanceId, node);
        return node;
    }

    public IRegularNode buildExistingRegularNode(String typeName, long instanceId) {
        KeyValueNode node = this.idToNodeMap.get(instanceId);
        if (null == node) {
            node = new KeyValueNode(this, typeName, instanceId);
            this.idToNodeMap.put(instanceId, node);
        }
        return node;
    }

    @Override
    public INode buildConstantNode(long constantId) {
        // This is just to point out that constantIds are always negative.
        RuntimeAssertionError.assertTrue(constantId < 0);
        return new ConstantNode(constantId);
    }

    @Override
    public INode buildClassNode(String className) {
        return new ClassNode(className);
    }

    @Override
    public void flushWrites() {
        lazyComputeHash();
        StreamingPrimitiveCodec.Encoder encoder = new StreamingPrimitiveCodec.Encoder();
        encoder.encodeLong(this.nextInstanceId);
        encoder.encodeLong(this.instanceIdBias);
        
        // Number of levels.
        encoder.encodeInt(this.merkleTree.length);
        // Each level.
        for (int[] level : this.merkleTree) {
            encoder.encodeInt(level.length);
            for (int elt : level) {
                encoder.encodeInt(elt);
            }
        }
        byte[] rawData = encoder.toBytes();
        this.store.putStorage(this.address, StorageKeys.INTERNAL_DATA, rawData);
    }

    @Override
    public int simpleHashCode() {
        return lazyComputeHash();
    }

    @Override
    public long gc() {
        // As a relatively simple proof-of-concept, we will build a copying semi-space collector (scavenger) which uses instanceIdBias
        // to define the "low" and "high" spaces (if we assume less than HIGH_RANGE_BIAS instances are ever in storage at once.
        
        // First, determine the target bias and start our new instanceId counter (since we are re-assigning these).
        long targetBias = (0L == this.instanceIdBias) ? HIGH_RANGE_BIAS : 0L;
        long nextInstanceId = 1L;
        
        // Create the old-new instanceId fixup map.
        Map<Long, Long> instanceIdFixups = new HashMap<>();
        
        // Begin the GC:  set the next scan pointer to 0, read the statics extent (the implicit 0), and start the GC.
        // NOTE:  The statics don't move, so we update them in place (just a single SerializedRepresentation).
        long nextScanInstanceId = 0L;
        byte[] currentKey = StorageKeys.CLASS_STATICS;
        SerializedRepresentation scanningRepresentation = getRoot();
        while (null != scanningRepresentation) {
            boolean didWrite = false;
            INode[] refs = scanningRepresentation.references;
            for (int i = 0; i < refs.length; ++i) {
                INode ref = refs[i];
                // Class and constant refs don't change.
                if (ref instanceof KeyValueNode) {
                    KeyValueNode node = (KeyValueNode)ref;
                    long oldTargetInstanceId = node.getInstanceId();
                    long newTargetInstanceId = 0L;
                    if (!instanceIdFixups.containsKey(oldTargetInstanceId)) {
                        // Allocate this instance and copy the object.
                        newTargetInstanceId = nextInstanceId;
                        nextInstanceId += 1;
                        instanceIdFixups.put(oldTargetInstanceId, newTargetInstanceId);
                        byte[] rawData = this.store.getStorage(this.address, StorageKeys.forInstance(oldTargetInstanceId + this.instanceIdBias));
                        RuntimeAssertionError.assertTrue(null != rawData);
                        RuntimeAssertionError.assertTrue(rawData.length > 0);
                        this.store.putStorage(this.address, StorageKeys.forInstance(newTargetInstanceId + targetBias), rawData);
                    } else {
                        newTargetInstanceId = instanceIdFixups.get(oldTargetInstanceId);
                    }
                    // Fixup the reference.
                    if (oldTargetInstanceId != newTargetInstanceId) {
                        refs[i] = new KeyValueNode(this, node.getInstanceClassName(), newTargetInstanceId);
                        didWrite = true;
                    }
                }
            }
            if (didWrite) {
                this.store.putStorage(this.address, currentKey, KeyValueCodec.encode(scanningRepresentation));
            }
            nextScanInstanceId += 1;
            if (nextScanInstanceId < nextInstanceId) {
                // Load the next SerializedRepresentation and continue the collection.
                currentKey = StorageKeys.forInstance(nextScanInstanceId + targetBias);
                byte[] nextNewInstanceToScan = this.store.getStorage(this.address, currentKey);
                scanningRepresentation = KeyValueCodec.decode(this, nextNewInstanceToScan);
            } else {
                // We are done so fall out.
                currentKey = null;
                scanningRepresentation = null;
            }
        }
        
        // Rebuild the Merkle tree.
        this.merkleTree = new int[0][];
        this.dirtyLeaves = new boolean[0];
        for (long i = 1L; i < nextInstanceId; ++i) {
            int index = (int)i - 1;
            ensureTreeSize(index);
            // Hash the data into the leaf.
            byte[] data = this.store.getStorage(this.address, StorageKeys.forInstance(i + targetBias));
            this.merkleTree[0][index] = Arrays.hashCode(data);
            this.dirtyLeaves[index] = true;
        }
        
        // Save the internal state
        for (long i = 1L; i < this.nextInstanceId; ++i) {
            this.store.putStorage(this.address, StorageKeys.forInstance(i + this.instanceIdBias), new byte[0]);
        }
        // The number of instances freed is just the difference between our next ID incrementor before and after the GC.
        long instancesFreed = (this.nextInstanceId - nextInstanceId);
        this.nextInstanceId = nextInstanceId;
        this.instanceIdBias = targetBias;
        flushWrites();
        return instancesFreed;
    }

    @Override
    public String toString() {
        return "KeyValueObjectGraph @" + Arrays.toString(this.address);
    }

    /**
     * Called by KeyValueNode to create the instance it references (which it, internally, caches for any future requests).
     * 
     * @param instanceClassName
     * @param instanceId
     * @return
     */
    public org.aion.avm.shadow.java.lang.Object createInstanceStubForNode(String instanceClassName, IRegularNode callingNode) {
        IPersistenceToken token = this.tokenBuilder.apply(callingNode);
        try {
            return (org.aion.avm.shadow.java.lang.Object)
                    this.constructorCache.getConstructorForClassName(instanceClassName)
                        .newInstance(this.logicalDeserializer, token);
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            // Any errors like this would have been caught earlier.
            throw RuntimeAssertionError.unexpected(e);
        }
    }

    public byte[] loadStorageForInstance(long instanceId) {
        return this.store.getStorage(this.address, StorageKeys.forInstance(instanceId + this.instanceIdBias));
    }

    public void storeDataForInstance(long instanceId, byte[] data) {
        this.store.putStorage(this.address, StorageKeys.forInstance(instanceId + this.instanceIdBias), data);
        // NOTE:  Just as a proof of concept, we build the Merkle tree on this raw data (it should actually be the SerializedRepresentation).
        int index = (int)instanceId;
        ensureTreeSize(index);
        // Hash the data into the leaf.
        this.merkleTree[0][index] = Arrays.hashCode(data);
        this.dirtyLeaves[index] = true;
    }

    private void ensureTreeSize(int index) {
        if (index >= this.dirtyLeaves.length) {
            // Grow the tree.
            int[][] newTree = new int[this.merkleTree.length + 1][];
            for (int i = 0; i < this.merkleTree.length; ++i) {
                newTree[i] = new int[this.merkleTree[i].length * 2];
                System.arraycopy(this.merkleTree[i], 0, newTree[i], 0, this.merkleTree[i].length);
            }
            // (the new root goes at the top).
            newTree[newTree.length - 1] = new int[1];
            this.merkleTree = newTree;
            
            // And the leaves.
            int oldDirtyLength = this.dirtyLeaves.length;
            boolean[] newDirty = new boolean[(oldDirtyLength > 0) ? (oldDirtyLength * 2) : 1];
            System.arraycopy(this.dirtyLeaves, 0, newDirty, 0, oldDirtyLength);
            this.dirtyLeaves = newDirty;
            
            // Just to make sure that the new parts of the tree are populated, mark them as dirty (although they are just empty).
            for (int i = oldDirtyLength; i < this.dirtyLeaves.length; ++i) {
                this.dirtyLeaves[i] = true;
            }
        }
    }

    private int lazyComputeHash() {
        // Recalculate the dirty leaves and push this up the tree.
        int branchLevel = 1;
        boolean[] leafLevelDirty = this.dirtyLeaves;
        while (branchLevel < this.merkleTree.length) {
            boolean[] branchLevelDirty = new boolean[leafLevelDirty.length / 2];
            for (int i = 0; i < branchLevelDirty.length; ++i) {
                boolean shouldRecalculate = (leafLevelDirty[2*i] || leafLevelDirty[2*i + 1]);
                if (shouldRecalculate) {
                    int[] leafHashes = this.merkleTree[branchLevel - 1];
                    this.merkleTree[branchLevel][i] = (leafHashes[2*i] ^ leafHashes[2*i + 1]);
                }
                branchLevelDirty[i] = shouldRecalculate;
            }
            // Advance up the tree.
            branchLevel += 1;
            leafLevelDirty = branchLevelDirty;
        }
        // Wipe the leaves.
        Arrays.fill(this.dirtyLeaves, false);
        
        // Return the root of the tree.
        return (this.merkleTree.length > 0)
                ? this.merkleTree[this.merkleTree.length - 1][0]
                : 0;
    }
}
