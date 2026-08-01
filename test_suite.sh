#!/bin/bash
set -e

echo "=== Compiling ACE-ORS ==="
./gradlew build -x test

echo "=== Running Job Shop Robust Test (ft06) ==="
java -jar build/libs/ACE-2.6.jar /Users/jlopez/PhD/RobustSolutions/jobshop/JSPLIB/xmls/SchedulingJS-ft06.xml -tdom=true -robvars="match-s[" -sSch="(x+k_offset==y,k)" -k_offset=1 -k=1 -je=true -s=all -sos=0 -aaa=true -dsp=false -da=true -varh=WdegOnDom -t=20s > ft06_test.log
if grep -q "OPTIMUM FOUND" ft06_test.log && grep -q "cost='63'" ft06_test.log; then
    echo "✅ ft06 Custom Propagator Passed (Optimum Found: 63)"
else
    echo "❌ ft06 Custom Propagator Failed"
    exit 1
fi

echo "=== Running Job Shop Robust Test CRP (ft06) ==="
java -jar build/libs/ACE-2.6.jar /Users/jlopez/PhD/RobustSolutions/jobshop/JSPLIB/xmls_CRP/SchedulingJS_CRP-ft06.xml -je=true -s=all -sos=0 -aaa=true -dsp=false -da=true -varh=WdegOnDom -t=20s > ft06_crp_test.log
if grep -q "OPTIMUM FOUND" ft06_crp_test.log && grep -q "cost='63'" ft06_crp_test.log; then
    echo "✅ ft06 CRP Passed (Optimum Found: 63)"
else
    echo "❌ ft06 CRP Failed"
    exit 1
fi

echo "=== Running Job Shop Toy Test (ToySche) ==="
java -jar build/libs/ACE-2.6.jar /Users/jlopez/PhD/RobustSolutions/toyProblems/ToySche.xml -tdom=true -robvars="match-s[" -sSch="(x+k_offset==y,k)" -k_offset=1 -k=1 -je=true -s=all -sos=0 -aaa=true -dsp=false -da=true -varh=WdegOnDom -t=5s > toysche_test.log
if grep -q "COMPLETE EXPLORATION" toysche_test.log || grep -q "FULL_EXPLORATION" toysche_test.log; then
    echo "✅ ToySche Passed"
else
    echo "❌ ToySche Failed"
    exit 1
fi

echo "=== All Core Tests Passed Successfully ==="
