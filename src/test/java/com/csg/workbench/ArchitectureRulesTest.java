package com.csg.workbench;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.freeze.FreezingArchRule.freeze;

import com.csg.workbench.problem.ProblemMapper;
import com.csg.workbench.problem.ProblemV11Service;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 架构护栏规则（W1-A D4）：
 * 使用 FreezingArchRule 冻结存量违规，确保新增代码零新增违规。
 */
@AnalyzeClasses(packages = "com.csg.workbench", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureRulesTest {

    private static final Set<String> PROBLEM_STATE_WRITE_METHODS = Collections.unmodifiableSet(
        new HashSet<String>(Arrays.asList(
            "insertProblem",
            "insertProblemV11",
            "changeState",
            "assignOwner",
            "assignOwnerAndState",
            "suspendProblem",
            "closeWithOldPart",
            "setResume"
        ))
    );

    @ArchTest
    static final ArchRule integration_public_methods_must_not_use_map = freeze(
        methods().that().areDeclaredInClassesThat().resideInAPackage("..integration..")
            .and().arePublic()
            .should(notExposeMapInSignature())
            .as("..integration.. 包的 public 方法入参和返回值不得使用 java.util.Map")
    );

    @ArchTest
    static final ArchRule controllers_must_not_depend_on_mappers = freeze(
        noClasses().that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Mapper")
            .as("..Controller 不得直接依赖 ..Mapper")
    );

    @ArchTest
    static final ArchRule only_problem_v11_service_may_call_problem_state_write_methods = freeze(
        noClasses().that().doNotHaveFullyQualifiedName(ProblemV11Service.class.getName())
            .should().callMethodWhere(isProblemMapperStateWriteCall())
            .as("只有 ProblemV11Service 可以调用 ProblemMapper 的写状态方法（存量 ProblemService 调用已冻结）")
    );

    private static ArchCondition<JavaMethod> notExposeMapInSignature() {
        return new ArchCondition<JavaMethod>("not have Map parameter or return type") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                if (method.getRawReturnType().isAssignableTo(Map.class)) {
                    String message = method.getFullName() + " returns " + method.getRawReturnType().getName();
                    events.add(SimpleConditionEvent.violated(method, message));
                }
                for (JavaClass paramType : method.getRawParameterTypes()) {
                    if (paramType.isAssignableTo(Map.class)) {
                        String message = method.getFullName() + " accepts parameter " + paramType.getName();
                        events.add(SimpleConditionEvent.violated(method, message));
                    }
                }
            }
        };
    }

    private static DescribedPredicate<JavaCall<?>> isProblemMapperStateWriteCall() {
        return new DescribedPredicate<JavaCall<?>>("call ProblemMapper state-mutating method") {
            @Override
            public boolean test(JavaCall<?> call) {
                return call.getTargetOwner().isEquivalentTo(ProblemMapper.class)
                    && PROBLEM_STATE_WRITE_METHODS.contains(call.getTarget().getName());
            }
        };
    }
}
