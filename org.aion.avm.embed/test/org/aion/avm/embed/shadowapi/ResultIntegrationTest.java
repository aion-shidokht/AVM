package org.aion.avm.embed.shadowapi;

import avm.Address;
import avm.Result;

import org.aion.avm.embed.AvmRule;
import org.aion.avm.tooling.ABIUtil;
import org.junit.*;

import java.math.BigInteger;


public class ResultIntegrationTest {
    @ClassRule
    public static AvmRule avmRule = new AvmRule(false);

    private static Address from = avmRule.getPreminedAccount();
    private static Address dappAddr;

    @BeforeClass
    public static void setUp() {
        byte[] jar = avmRule.getDappBytes(ResultTestTarget.class, new byte[0]);
        dappAddr = avmRule.deploy(from, BigInteger.ZERO, jar).getDappAddress();
    }

    @Test
    public void testToString() {
        Object result = call("getToStringSuccessTrue");
        Assert.assertEquals(
                "success:true, returnData:000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
                result.toString());

        result = call("getToStringSuccessFalse");
        Assert.assertEquals(
                "success:false, returnData:000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
                result.toString());
    }

    @Test
    public void testEquals() {
        Object result = call("getEquals");
        Assert.assertEquals(true, result);

        result = call("getUnequalsSameSuccessDiffData");
        Assert.assertEquals(false, result);

        result = call("getUnequalsDiffSuccessSameData");
        Assert.assertEquals(false, result);
    }

    @Test
    public void testHashCode() {
        Object result = call("getHashCodeSuccessTrue");
        Assert.assertEquals(497, result);

        result = call("getHashCodeSuccessFalse");
        Assert.assertEquals(496, result);
    }

    private Object call(String methodName, Result ...results) {
        byte[] txDataMethodArguments = ABIUtil.encodeMethodArguments(methodName, (Object[]) results);
        return avmRule.call(from, dappAddr, BigInteger.ZERO, txDataMethodArguments).getDecodedReturnData();
    }
}
