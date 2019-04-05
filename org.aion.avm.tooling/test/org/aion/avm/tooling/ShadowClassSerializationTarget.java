package org.aion.avm.tooling;

import java.math.BigDecimal;
import java.math.BigInteger;
import avm.Blockchain;
import org.aion.avm.tooling.abi.Callable;

public class ShadowClassSerializationTarget {
    private static BigInteger bigNegative = BigInteger.valueOf(-1);
    private static BigInteger bigBigNegative = BigInteger.TEN.pow(10_000).negate();
    private static BigInteger bigNeutral = BigInteger.valueOf(0);
    private static BigInteger bigPositive = BigInteger.valueOf(1);
    private static BigInteger bigBigPositive = BigInteger.TEN.pow(10_000);
    private static BigDecimal bigNegativeDecimal = BigDecimal.valueOf(-1);
    private static BigDecimal bigBigNegativeDecimal = BigDecimal.TEN.pow(10_000).negate();
    private static BigDecimal bigNeutralDecimal = BigDecimal.valueOf(0);
    private static BigDecimal bigPositiveDecimal = BigDecimal.valueOf(1);
    private static BigDecimal bigBigPositiveDecimal = BigDecimal.TEN.pow(10_000);

    @Callable
    public static void checkBigIntegerSerialization() {
        Blockchain.require(bigNegative.equals(BigInteger.valueOf(-1)));
        Blockchain.require(bigBigNegative.equals(BigInteger.valueOf(10).pow(10_000).multiply(BigInteger.valueOf(-1))));
        Blockchain.require(bigNeutral.equals(BigInteger.valueOf(0)));
        Blockchain.require(bigPositive.equals(BigInteger.valueOf(1)));
        Blockchain.require(bigBigPositive.equals(BigInteger.valueOf(10).pow(10_000)));
    }

    @Callable
    public static void checkBigDecimalSerialization() {
        Blockchain.require(bigNegativeDecimal.equals(BigDecimal.valueOf(-1)));
        Blockchain.require(bigBigNegativeDecimal.equals(BigDecimal.valueOf(10).pow(10_000).multiply(BigDecimal.valueOf(-1))));
        Blockchain.require(bigNeutralDecimal.equals(BigDecimal.valueOf(0)));
        Blockchain.require(bigPositiveDecimal.equals(BigDecimal.valueOf(1)));
        Blockchain.require(bigBigPositiveDecimal.equals(BigDecimal.valueOf(10).pow(10_000)));
    }

}
