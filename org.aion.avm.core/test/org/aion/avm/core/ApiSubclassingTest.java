package org.aion.avm.core;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;
import org.aion.kernel.AvmWrappedTransactionResult.AvmInternalError;
import org.aion.kernel.TestingState;
import org.aion.types.AionAddress;
import org.aion.types.Transaction;
import org.aion.avm.core.blockchainruntime.EmptyCapabilities;
import org.aion.avm.core.dappreading.UserlibJarBuilder;
import org.aion.avm.core.util.Helpers;
import org.aion.avm.userlib.CodeAndArguments;
import org.aion.kernel.TestingBlock;
import org.aion.types.TransactionResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ApiSubclassingTest {
    private TestingState kernel;
    private AvmImpl avm;
    private AionAddress deployer = TestingState.PREMINED_ADDRESS;

    @Before
    public void setup() {
        TestingBlock block = new TestingBlock(new byte[32], 1, Helpers.randomAddress(), System.currentTimeMillis(), new byte[0]);
        this.kernel = new TestingState(block);
        this.avm = CommonAvmFactory.buildAvmInstanceForConfiguration(new EmptyCapabilities(), new AvmConfiguration());
    }

    @After
    public void teardown() {
        this.avm.shutdown();
    }

    @Test
    public void testDeployAndCallContractWithAbiSubclasses() {
        byte[] jar = new CodeAndArguments(UserlibJarBuilder.buildJarForMainAndClassesAndUserlib(ApiSubclassingTarget.class), null).encodeToBytes();
        Transaction transaction = AvmTransactionUtil.create(this.deployer, this.kernel.getNonce(deployer), BigInteger.ZERO, jar, 5_000_000, 1);
        TransactionResult result = this.avm.run(this.kernel, new Transaction[] {transaction}, ExecutionType.ASSUME_MAINCHAIN, kernel.getBlockNumber() - 1)[0].getResult();
        assertEquals(AvmInternalError.FAILED_REJECTED_CLASS.error, result.transactionStatus.causeOfError);
    }

}
