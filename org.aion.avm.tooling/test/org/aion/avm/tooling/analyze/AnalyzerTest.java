package org.aion.avm.tooling.analyze;

import org.aion.avm.tooling.util.Utilities;
import org.junit.Assert;
import org.junit.Test;

public class AnalyzerTest {

    @Test
    public void analyze() {
        byte[] classBytes = Utilities.loadRequiredResourceAsBytes(Utilities.fulllyQualifiedNameToInternalName(ExampleClass.class.getName())+".class");
        ConstantPoolBuilder.ClassConstantSizeInfo info = ConstantPoolBuilder.getConstantPoolInfo(classBytes);
        Assert.assertEquals(info.constantTypeCount.size(), 14);
    }

}
