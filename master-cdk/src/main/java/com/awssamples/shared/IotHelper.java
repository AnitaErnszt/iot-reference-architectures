package com.awssamples.shared;

import com.awssamples.iam.Permissions;
import org.jetbrains.annotations.NotNull;
import software.amazon.awscdk.core.Fn;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.PolicyStatementProps;
import software.amazon.awscdk.services.iot.CfnTopicRule;
import software.amazon.awscdk.services.lambda.CfnPermission;
import software.amazon.awscdk.services.lambda.CfnPermissionProps;
import software.amazon.awscdk.services.lambda.Function;

import java.util.Arrays;

import static com.awssamples.iam.Permissions.*;
import static java.util.Collections.singletonList;

public class IotHelper {
    public static CfnPermission allowIotTopicRuleToInvokeLambdaFunction(Stack stack, CfnTopicRule topicRule, Function function, String permissionNamePrefix) {
        String iotServicePrincipal = Fn.join(".", Arrays.asList("iot", stack.getUrlSuffix()));
        CfnPermissionProps cfnPermissionProps = CfnPermissionProps.builder()
                .sourceArn(topicRule.getAttrArn())
                .action(Permissions.LAMBDA_INVOKE_FUNCTION)
                .principal(iotServicePrincipal)
                .sourceAccount(stack.getAccount())
                .functionName(function.getFunctionArn())
                .build();

        return new CfnPermission(stack, permissionNamePrefix + "LambdaInvocationPermissions", cfnPermissionProps);
    }

    @NotNull
    public static PolicyStatement getPublishToTopicPolicyStatement(Stack stack, String topic) {
        PolicyStatementProps iotPolicyStatementProps = PolicyStatementProps.builder()
                .effect(Effect.ALLOW)
                .resources(singletonList(Fn.join("", Arrays.asList("arn:aws:iot:", stack.getRegion(), ":", stack.getAccount(), ":topic/", topic))))
                .actions(singletonList(IOT_PUBLISH_PERMISSION))
                .build();

        return new PolicyStatement(iotPolicyStatementProps);
    }

    @NotNull
    public static PolicyStatement getCreateThingPolicyStatement() {
        return getAllowAllPolicyStatement(IOT_CREATE_THING_PERMISSION);
    }

    @NotNull
    public static PolicyStatement getCreateThingGroupPolicyStatement() {
        return getAllowAllPolicyStatement(IOT_CREATE_THING_GROUP_PERMISSION);
    }

    @NotNull
    public static PolicyStatement getUpdateThingGroupsForThingPolicyStatement() {
        return getAllowAllPolicyStatement(IOT_UPDATE_THING_GROUPS_FOR_THING_PERMISSION);
    }

    @NotNull
    public static PolicyStatement getUpdateThingShadowPolicyStatement() {
        return getAllowAllPolicyStatement(IOT_UPDATE_THING_SHADOW_PERMISSION);
    }
}
