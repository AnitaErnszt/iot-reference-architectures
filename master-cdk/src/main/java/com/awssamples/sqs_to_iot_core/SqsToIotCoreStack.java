package com.awssamples.sqs_to_iot_core;

import com.awssamples.iam.Permissions;
import com.awssamples.iam.policies.CloudWatchEventsPolicies;
import com.awssamples.iam.policies.LambdaPolicies;
import com.awssamples.shared.CdkHelper;
import io.vavr.control.Try;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Fn;
import software.amazon.awscdk.core.RemovalPolicy;
import software.amazon.awscdk.services.dynamodb.*;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.iot.CfnTopicRule;
import software.amazon.awscdk.services.iot.CfnTopicRuleProps;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.*;
import software.amazon.awscdk.services.sqs.Queue;
import software.amazon.awscdk.services.sqs.QueueProps;

import java.io.File;
import java.util.*;

import static com.awssamples.iam.policies.IotPolicies.getSearchIndexPolicy;
import static com.awssamples.shared.IotHelper.*;
import static com.awssamples.shared.RoleHelper.*;
import static java.util.Collections.singletonList;

public class SqsToIotCoreStack extends software.amazon.awscdk.core.Stack {
    public static final String AWS_IOT_SQL_VERSION = "2016-03-23";
    public static final Logger log = LoggerFactory.getLogger(SqsToIotCoreStack.class);
    public static final String INBOUND_SQS_QUEUE_ARN_ENVIRONMENT_VARIABLE = "INBOUND_SQS_QUEUE_ARN";
    public static final String OUTBOUND_SQS_QUEUE_ARN_ENVIRONMENT_VARIABLE = "OUTBOUND_SQS_QUEUE_ARN";
    public static final String UUID_KEY_ENVIRONMENT_VARIABLE = "UUID_KEY";
    public static final String MESSAGE_ID_KEY_ENVIRONMENT_VARIABLE = "MESSAGE_ID_KEY";
    public static final String DYNAMO_DB_TABLE_ARN = "dynamoDbTableArn";
    public static final String INBOUND_SQS_QUEUE_ARN = "inboundSqsQueueArn";
    public static final String OUTBOUND_SQS_QUEUE_ARN = "outboundSqsQueueArn";
    public static final String UUID_KEY = "uuidKey";
    public static final String MESSAGE_ID_KEY = "messageIdKey";
    public static final String HANDLE_REQUEST = "handleRequest";
    public static final String MESSAGE_ID_DYNAMO_DB_COLUMN_NAME = "messageId";
    public static final String UUID_DYNAMO_DB_COLUMN_NAME = "uuid";
    public static final String HANDLER_PACKAGE = "com.awssamples.iot.dynamodb.api.handlers";
    public static final String SQS_EVENT_HANDLER = String.join(".", HANDLER_PACKAGE, "HandleSqsEvent");
    public static final String REQUEST = "request";
    public static final String RESPONSE = "response";
    public static final String LAMBDA_INVOCATION_PERMISSIONS_PREFIX = "LambdaInvocationPermissions";
    public static final String MQTT_TOPIC_SEPARATOR = "/";
    public static final String NO_SEPARATOR = "";
    public static final String MESSAGE_ROLE = "MessageRole";
    public static final String MESSAGE_FUNCTION = "MessageFunction";
    public static final String MESSAGE_RULE = "MessageRule";

    // GET
    public static final String GET_METHOD_NAME = String.join(".", HANDLER_PACKAGE, "HandleIotGetEvent");
    public static final String GET = "get";
    public static final String GET_REQUEST_TOPIC_PREFIX = String.join(MQTT_TOPIC_SEPARATOR, REQUEST, GET);
    public static final String GET_RESPONSE_TOPIC_PREFIX = String.join(MQTT_TOPIC_SEPARATOR, RESPONSE, GET);
    public static final String GET_ROLE_NAME = String.join(NO_SEPARATOR, GET, MESSAGE_ROLE);
    public static final String GET_LAMBDA_FUNCTION_NAME = String.join(NO_SEPARATOR, GET, MESSAGE_FUNCTION);
    public static final String GET_RULE_NAME = String.join(NO_SEPARATOR, GET, MESSAGE_RULE);
    public static final String GET_LAMBDA_PERMISSIONS = String.join(NO_SEPARATOR, LAMBDA_INVOCATION_PERMISSIONS_PREFIX, GET);

    // QUERY
    public static final String QUERY_METHOD_NAME = String.join(".", HANDLER_PACKAGE, "HandleIotQueryEvent");
    public static final String QUERY = "query";
    public static final String QUERY_REQUEST_TOPIC_PREFIX = String.join(MQTT_TOPIC_SEPARATOR, REQUEST, QUERY);
    public static final String QUERY_RESPONSE_TOPIC_PREFIX = String.join(MQTT_TOPIC_SEPARATOR, RESPONSE, QUERY);
    public static final String QUERY_ROLE_NAME = String.join(NO_SEPARATOR, QUERY, MESSAGE_ROLE);
    public static final String QUERY_LAMBDA_FUNCTION_NAME = String.join(NO_SEPARATOR, QUERY, MESSAGE_FUNCTION);
    public static final String QUERY_RULE_NAME = String.join(NO_SEPARATOR, QUERY, MESSAGE_RULE);
    public static final String QUERY_LAMBDA_PERMISSIONS = String.join(NO_SEPARATOR, LAMBDA_INVOCATION_PERMISSIONS_PREFIX, QUERY);

    // DELETE
    public static final String DELETE_METHOD_NAME = String.join(".", HANDLER_PACKAGE, "HandleIotDeleteEvent");
    public static final String DELETE = "delete";
    public static final String DELETE_REQUEST_TOPIC_PREFIX = String.join(MQTT_TOPIC_SEPARATOR, REQUEST, DELETE);
    public static final String DELETE_RESPONSE_TOPIC_PREFIX = String.join(MQTT_TOPIC_SEPARATOR, RESPONSE, DELETE);
    public static final String DELETE_ROLE_NAME = String.join(NO_SEPARATOR, DELETE, MESSAGE_ROLE);
    public static final String DELETE_LAMBDA_FUNCTION_NAME = String.join(NO_SEPARATOR, DELETE, MESSAGE_FUNCTION);
    public static final String DELETE_RULE_NAME = String.join(NO_SEPARATOR, DELETE, MESSAGE_RULE);
    public static final String DELETE_LAMBDA_PERMISSIONS = String.join(NO_SEPARATOR, LAMBDA_INVOCATION_PERMISSIONS_PREFIX, DELETE);

    // NEXT
    public static final String NEXT_METHOD_NAME = String.join(".", HANDLER_PACKAGE, "HandleIotNextEvent");
    public static final String NEXT = "next";
    public static final String NEXT_REQUEST_TOPIC_PREFIX = String.join(MQTT_TOPIC_SEPARATOR, REQUEST, NEXT);
    public static final String NEXT_RESPONSE_TOPIC_PREFIX = String.join(MQTT_TOPIC_SEPARATOR, RESPONSE, NEXT);
    public static final String NEXT_ROLE_NAME = String.join(NO_SEPARATOR, NEXT, MESSAGE_ROLE);
    public static final String NEXT_LAMBDA_FUNCTION_NAME = String.join(NO_SEPARATOR, NEXT, MESSAGE_FUNCTION);
    public static final String NEXT_RULE_NAME = String.join(NO_SEPARATOR, NEXT, MESSAGE_RULE);
    public static final String NEXT_LAMBDA_PERMISSIONS = String.join(NO_SEPARATOR, LAMBDA_INVOCATION_PERMISSIONS_PREFIX, NEXT);

    // DEVICES
    // Decide to use the registry or Dynamo DB here
    public static final String DEVICES_DYNAMODB_METHOD_NAME = String.join(".", HANDLER_PACKAGE, "HandleIotDevicesDynamoDbEvent");
    public static final String DEVICES_REGISTRY_METHOD_NAME = String.join(".", HANDLER_PACKAGE, "HandleIotDevicesRegistryEvent");
    public static final String DEVICES = "devices";
    public static final String DEVICES_REQUEST_TOPIC_PREFIX = String.join(MQTT_TOPIC_SEPARATOR, REQUEST, DEVICES);
    public static final String DEVICES_RESPONSE_TOPIC_PREFIX = String.join(MQTT_TOPIC_SEPARATOR, RESPONSE, DEVICES);
    public static final String DEVICES_ROLE_NAME = String.join(NO_SEPARATOR, DEVICES, MESSAGE_ROLE);
    public static final String DEVICES_LAMBDA_FUNCTION_NAME = String.join(NO_SEPARATOR, DEVICES, MESSAGE_FUNCTION);
    public static final String DEVICES_RULE_NAME = String.join(NO_SEPARATOR, DEVICES, MESSAGE_RULE);
    public static final String DEVICES_LAMBDA_PERMISSIONS = String.join(NO_SEPARATOR, LAMBDA_INVOCATION_PERMISSIONS_PREFIX, DEVICES);

    // SEND
    public static final String SEND_METHOD_NAME = String.join(".", HANDLER_PACKAGE, "HandleIotSendEvent");
    public static final String SEND = "send";
    public static final String SEND_REQUEST_TOPIC_PREFIX = String.join(MQTT_TOPIC_SEPARATOR, REQUEST, SEND);
    public static final String SEND_RESPONSE_TOPIC_PREFIX = String.join(MQTT_TOPIC_SEPARATOR, RESPONSE, SEND);
    public static final String SEND_ROLE_NAME = String.join(NO_SEPARATOR, SEND, MESSAGE_ROLE);
    public static final String SEND_LAMBDA_FUNCTION_NAME = String.join(NO_SEPARATOR, SEND, MESSAGE_FUNCTION);
    public static final String SEND_RULE_NAME = String.join(NO_SEPARATOR, SEND, MESSAGE_RULE);
    public static final String SEND_LAMBDA_PERMISSIONS = String.join(NO_SEPARATOR, LAMBDA_INVOCATION_PERMISSIONS_PREFIX, SEND);

    // NOTIFICATION
    public static final String NOTIFICATION_METHOD_NAME = String.join(".", HANDLER_PACKAGE, "HandleIotNotificationEvent");
    public static final String NOTIFICATION = "notification";
    public static final String NOTIFICATION_TOPIC_FILTER = String.join(MQTT_TOPIC_SEPARATOR, NOTIFICATION, "+");
    public static final String NOTIFICATION_ROLE_NAME = String.join(NO_SEPARATOR, NOTIFICATION, MESSAGE_ROLE);
    public static final String NOTIFICATION_LAMBDA_FUNCTION_NAME = String.join(NO_SEPARATOR, NOTIFICATION, MESSAGE_FUNCTION);
    public static final String NOTIFICATION_RULE_NAME = String.join(NO_SEPARATOR, NOTIFICATION, MESSAGE_RULE);
    public static final String NOTIFICATION_LAMBDA_PERMISSIONS = String.join(NO_SEPARATOR, LAMBDA_INVOCATION_PERMISSIONS_PREFIX, NOTIFICATION);

    // REPUBLISH
    public static final String REPUBLISH = "republish";
    public static final String REPUBLISH_ROLE_NAME = String.join(NO_SEPARATOR, REPUBLISH, MESSAGE_ROLE);
    public static final String POLLY_REPUBLISH_RULE_NAME = String.join(NO_SEPARATOR, REPUBLISH, "Polly", MESSAGE_RULE);
    public static final String FAX_REPUBLISH_RULE_NAME = String.join(NO_SEPARATOR, REPUBLISH, "Fax", MESSAGE_RULE);

    public static final String DEFAULT_UUID_KEY = "thingName";
    public static final String DEFAULT_MESSAGE_ID_KEY = "epochTime";
    public static final String MULTI_LEVEL_MQTT_WILDCARD = "#";
    public static final String REQUEST_TOPIC_PREFIX_KEY = "RequestTopicPrefix";
    public static final String RESPONSE_TOPIC_PREFIX_KEY = "ResponseTopicPrefix";
    public static final String DEFAULT_SELECT_CLAUSE = "select *, topic() as topic";
    public final String projectDirectory = "../dynamodb-api/java/";
    public final List<File> projectDirectoryFiles = singletonList(new File(projectDirectory));
    public final String buildOutputDirectory = "build/libs/";
    public final String outputJar = "java-1.0-SNAPSHOT-all.jar";
    public final Duration queueVisibilityTimeout = Duration.seconds(30);
    // Queue visibility timeout must be greater than Lambda timeout
    public final Duration lambdaFunctionTimeout = Duration.seconds((int) queueVisibilityTimeout.toSeconds() / 2);
    // Whether to use DynamoDB or the IoT registry for device lookup
    private final boolean dynamoDbDeviceLookup = false;

    public SqsToIotCoreStack(final Construct parent, final String name) {
        super(parent, name);

        // Build all of the necessary JARs
        projectDirectoryFiles.forEach(this::buildJar);

        Optional<Map<String, String>> optionalArguments = CdkHelper.getArguments();

        // Get the queue ARN from the arguments or build the message queue and extract the ARN
        String inboundSqsQueueArn = optionalArguments
                .flatMap(this::getInboundQueueArn)
                .orElseGet(() -> buildInboundMessageQueue().getQueueArn());

        String outboundSqsQueueArn = optionalArguments
                .flatMap(this::getOutboundQueueArn)
                .orElseGet(() -> buildOutboundMessageQueue().getQueueArn());

        Table messageTable = buildMessageTable();

        String uuidKey = optionalArguments
                .flatMap(this::getUuid)
                .orElseGet(this::getDefaultUuidKeyAndWarn);

        String messageIdKey = optionalArguments
                .flatMap(this::getMessageId)
                .orElseGet(this::getDefaultMessageKeyIdAndWarn);

        // Resources to move messages from SQS to DynamoDB
        Role moveFromSqsToDynamoDbRole = buildMoveFromSqsToDynamoDbRole(inboundSqsQueueArn, messageTable);
        Function moveFromSqsToDynamoDb = buildMoveFromSqsToDynamoDbLambda(inboundSqsQueueArn, uuidKey, messageIdKey, messageTable, moveFromSqsToDynamoDbRole);
        buildEventSourceMapping(inboundSqsQueueArn, moveFromSqsToDynamoDb);

        // Default environment for all functions just contains the table name
        Map<String, String> defaultEnvironment = getDefaultEnvironment(messageTable);

        // Resources to get messages from DynamoDB and publish to IoT Core
        List<PolicyStatement> getItemPolicyStatementsForMessageTable = singletonList(getGetItemPolicyStatementForTable(messageTable));
        Role getRole = buildIotEventRoleForTopicPrefix(GET_ROLE_NAME, GET_RESPONSE_TOPIC_PREFIX, getItemPolicyStatementsForMessageTable);
        String getRequestTopicFilter = String.join(MQTT_TOPIC_SEPARATOR, GET_REQUEST_TOPIC_PREFIX, MULTI_LEVEL_MQTT_WILDCARD);
        Map<String, String> getMessageLambdaEnvironment = getGetMessageLambdaEnvironment();
        Function getLambda = buildIotEventLambda(GET_LAMBDA_FUNCTION_NAME, getRole, defaultEnvironment, getMessageLambdaEnvironment, GET_METHOD_NAME);
        CfnTopicRule getRule = buildIotEventRule(GET_RULE_NAME, getLambda, getRequestTopicFilter);
        allowIotTopicRuleToInvokeLambdaFunction(this, getRule, getLambda, GET_LAMBDA_PERMISSIONS);

        // Resources to query for available messages from DynamoDB and publish to IoT Core
        List<PolicyStatement> queryPolicyStatementsForMessageTable = singletonList(getQueryPolicyStatementForTable(messageTable));
        Role queryRole = buildIotEventRoleForTopicPrefix(QUERY_ROLE_NAME, QUERY_RESPONSE_TOPIC_PREFIX, queryPolicyStatementsForMessageTable);
        String queryRequestTopicFilter = String.join(MQTT_TOPIC_SEPARATOR, QUERY_REQUEST_TOPIC_PREFIX, MULTI_LEVEL_MQTT_WILDCARD);
        Map<String, String> queryMessageLambdaEnvironment = getQueryMessageLambdaEnvironment();
        Function queryLambda = buildIotEventLambda(QUERY_LAMBDA_FUNCTION_NAME, queryRole, defaultEnvironment, queryMessageLambdaEnvironment, QUERY_METHOD_NAME);
        CfnTopicRule queryRule = buildIotEventRule(QUERY_RULE_NAME, queryLambda, queryRequestTopicFilter);
        allowIotTopicRuleToInvokeLambdaFunction(this, queryRule, queryLambda, QUERY_LAMBDA_PERMISSIONS);

        // Resources to delete messages from DynamoD0
        List<PolicyStatement> deleteItemPolicyStatementsForMessageTable = singletonList(getDeleteItemPolicyStatementForTable(messageTable));
        Role deleteRole = buildIotEventRoleForTopicPrefix(DELETE_ROLE_NAME, DELETE_RESPONSE_TOPIC_PREFIX, deleteItemPolicyStatementsForMessageTable);
        String deleteRequestTopicFilter = String.join(MQTT_TOPIC_SEPARATOR, DELETE_REQUEST_TOPIC_PREFIX, MULTI_LEVEL_MQTT_WILDCARD);
        Map<String, String> deleteMessageLambdaEnvironment = getDeleteMessageLambdaEnvironment();
        Function deleteLambda = buildIotEventLambda(DELETE_LAMBDA_FUNCTION_NAME, deleteRole, defaultEnvironment, deleteMessageLambdaEnvironment, DELETE_METHOD_NAME);
        CfnTopicRule deleteRule = buildIotEventRule(DELETE_RULE_NAME, deleteLambda, deleteRequestTopicFilter);
        allowIotTopicRuleToInvokeLambdaFunction(this, deleteRule, deleteLambda, DELETE_LAMBDA_PERMISSIONS);

        // Resources to get the next message ID from DynamoDB
        List<PolicyStatement> nextItemPolicyStatementsForMessageTable = singletonList(getNextItemPolicyStatementForTable(messageTable));
        Role nextRole = buildIotEventRoleForTopicPrefix(NEXT_ROLE_NAME, NEXT_RESPONSE_TOPIC_PREFIX, nextItemPolicyStatementsForMessageTable);
        String nextRequestTopicFilter = String.join(MQTT_TOPIC_SEPARATOR, NEXT_REQUEST_TOPIC_PREFIX, MULTI_LEVEL_MQTT_WILDCARD);
        Map<String, String> nextMessageLambdaEnvironment = getNextMessageLambdaEnvironment();
        Function nextLambda = buildIotEventLambda(NEXT_LAMBDA_FUNCTION_NAME, nextRole, defaultEnvironment, nextMessageLambdaEnvironment, NEXT_METHOD_NAME);
        CfnTopicRule nextRule = buildIotEventRule(NEXT_RULE_NAME, nextLambda, nextRequestTopicFilter);
        allowIotTopicRuleToInvokeLambdaFunction(this, nextRule, nextLambda, NEXT_LAMBDA_PERMISSIONS);

        // Resources to get the list of devices with unread messages from DynamoDB
        String devicesRequestTopicFilter = String.join(MQTT_TOPIC_SEPARATOR, DEVICES_REQUEST_TOPIC_PREFIX, MULTI_LEVEL_MQTT_WILDCARD);
        Map<String, String> devicesMessageLambdaEnvironment = getDevicesMessageLambdaEnvironment();
        Function devicesLambda;

        if (dynamoDbDeviceLookup) {
            List<PolicyStatement> devicesDynamoDbPolicyStatementsForMessageTable = singletonList(getDevicesPolicyStatementForTable(messageTable));
            Role dynamoDbDevicesRole = buildIotEventRoleForTopicPrefix(DEVICES_ROLE_NAME, DEVICES_RESPONSE_TOPIC_PREFIX, devicesDynamoDbPolicyStatementsForMessageTable);
            Function dynamoDbDevicesLambda = buildIotEventLambda(DEVICES_LAMBDA_FUNCTION_NAME, dynamoDbDevicesRole, defaultEnvironment, devicesMessageLambdaEnvironment, DEVICES_DYNAMODB_METHOD_NAME);
            devicesLambda = dynamoDbDevicesLambda;
        } else {
            List<PolicyStatement> devicesRegistryPolicyStatements = singletonList(getSearchIndexPolicy());
            Role registryDevicesRole = buildIotEventRoleForTopicPrefix(DEVICES_ROLE_NAME, DEVICES_RESPONSE_TOPIC_PREFIX, devicesRegistryPolicyStatements);
            Function registryDevicesLambda = buildIotEventLambda(DEVICES_LAMBDA_FUNCTION_NAME, registryDevicesRole, defaultEnvironment, devicesMessageLambdaEnvironment, DEVICES_REGISTRY_METHOD_NAME);
            devicesLambda = registryDevicesLambda;
        }

        CfnTopicRule devicesRule = buildIotEventRule(DEVICES_RULE_NAME, devicesLambda, devicesRequestTopicFilter);
        allowIotTopicRuleToInvokeLambdaFunction(this, devicesRule, devicesLambda, DEVICES_LAMBDA_PERMISSIONS);

        // Resources to send messages to SQS
        List<PolicyStatement> sendToSqsPolicyStatements = singletonList(getSqsSendMessagePolicyStatement(outboundSqsQueueArn));
        Role sendRole = buildIotEventRoleForTopicPrefix(SEND_ROLE_NAME, SEND_RESPONSE_TOPIC_PREFIX, sendToSqsPolicyStatements);
        String sendRequestTopicFilter = String.join(MQTT_TOPIC_SEPARATOR, SEND_REQUEST_TOPIC_PREFIX, MULTI_LEVEL_MQTT_WILDCARD);
        Map<String, String> sendMessageLambdaEnvironment = getSendMessageLambdaEnvironment(outboundSqsQueueArn);
        Function sendLambda = buildIotEventLambda(SEND_LAMBDA_FUNCTION_NAME, sendRole, defaultEnvironment, sendMessageLambdaEnvironment, SEND_METHOD_NAME);
        CfnTopicRule sendRule = buildIotEventRule(SEND_RULE_NAME, sendLambda, sendRequestTopicFilter);
        allowIotTopicRuleToInvokeLambdaFunction(this, sendRule, sendLambda, SEND_LAMBDA_PERMISSIONS);

        // Resources to receive notifications and add devices to the registry
        List<PolicyStatement> notificationCreateThingPolicyStatements = Arrays.asList(getCreateThingPolicyStatement(),
                getUpdateThingShadowPolicyStatement(),
                getUpdateThingGroupsForThingPolicyStatement(),
                getCreateThingGroupPolicyStatement());
        Role notificationRole = buildRoleAssumedByLambda(this, NOTIFICATION_ROLE_NAME, notificationCreateThingPolicyStatements);
        Function notificationLambda = buildIotEventLambda(NOTIFICATION_LAMBDA_FUNCTION_NAME, notificationRole, defaultEnvironment, NOTIFICATION_METHOD_NAME);
        CfnTopicRule notificationRule = buildIotEventRule(NOTIFICATION_RULE_NAME, notificationLambda, NOTIFICATION_TOPIC_FILTER);
        allowIotTopicRuleToInvokeLambdaFunction(this, notificationRule, notificationLambda, NOTIFICATION_LAMBDA_PERMISSIONS);

        PolicyStatement publishToPiCoreCddPolicyStatement = getPublishToTopicPolicyStatement(this, "pi_Core/cdd/*");

        Role republishRole = buildRoleAssumedByIot(this, REPUBLISH_ROLE_NAME, Collections.singletonList(publishToPiCoreCddPolicyStatement));

        CfnTopicRule.RepublishActionProperty pollyRepublishActionProperty = CfnTopicRule.RepublishActionProperty.builder()
                .qos(0)
                .topic("pi_Core/cdd/asteriskpolly/input")
                .roleArn(republishRole.getRoleArn())
                .build();

        CfnTopicRule.RepublishActionProperty faxRepublishActionProperty = CfnTopicRule.RepublishActionProperty.builder()
                .qos(0)
                .topic("pi_Core/cdd/asteriskfax/input")
                .roleArn(republishRole.getRoleArn())
                .build();

        String faxCaseStatement = "CASE regexp_replace(regexp_replace(regexp_replace(StackStatus, '^.*ROLLBACK.*$', 'FAIL'), '^.*FAILED.*$', 'FAIL'), '^.*COMPLETE.*$', 'PASS') WHEN 'FAIL' THEN false ELSE true END";
        String callerIdMessageStatement = faxCaseStatement
                .replaceAll("false", "'CFN FAIL'")
                .replaceAll("true", "'CFN PASS'");
        String faxFullSqlStatement = "SELECT StackName as stack, " + callerIdMessageStatement + " as callerId, " + faxCaseStatement + " as pass FROM 'cfn_watcher/were_done'";

        String pollyFailMessage = "concat('We are sorry, the CloudFormation stack you attempted to launch, ', StackName, ' failed to launch. Please check the logs, fix your errors, and try again. We are sure it is just that simple. It is going to be OK. Do not cry. Do not fret. We will get through this together.')";
        String pollyPassMessage = "concat('Congratulations, the CloudFormation stack you attempted to launch, ', StackName, ' launched successfully. You are amazing!')";
        String pollyCaseMessageStatement = faxCaseStatement
                .replaceAll("false", pollyFailMessage)
                .replaceAll("true", pollyPassMessage);

        String pollyFullSqlStatement = "SELECT " + pollyCaseMessageStatement + " as message, " + callerIdMessageStatement + " as callerId FROM 'cfn_watcher/were_done'";

        buildIotEventRule(POLLY_REPUBLISH_RULE_NAME, pollyRepublishActionProperty, pollyFullSqlStatement);
        buildIotEventRule(FAX_REPUBLISH_RULE_NAME, faxRepublishActionProperty, faxFullSqlStatement);
    }

    @NotNull
    private String getDefaultUuidKeyAndWarn() {
        log.warn("No UUID key specified, using " + DEFAULT_UUID_KEY + " as default value");
        return DEFAULT_UUID_KEY;
    }

    @NotNull
    private String getDefaultMessageKeyIdAndWarn() {
        log.warn("No message ID key specified, using " + DEFAULT_MESSAGE_ID_KEY + " as default value");
        return DEFAULT_MESSAGE_ID_KEY;
    }

    private Optional<String> getInboundQueueArn(Map<String, String> map) {
        return Optional.ofNullable(map.get(INBOUND_SQS_QUEUE_ARN_ENVIRONMENT_VARIABLE));
    }

    private Optional<String> getOutboundQueueArn(Map<String, String> map) {
        return Optional.ofNullable(map.get(OUTBOUND_SQS_QUEUE_ARN_ENVIRONMENT_VARIABLE));
    }

    private Optional<String> getUuid(Map<String, String> map) {
        return Optional.ofNullable(map.get(UUID_KEY_ENVIRONMENT_VARIABLE));
    }

    private Optional<String> getMessageId(Map<String, String> map) {
        return Optional.ofNullable(map.get(MESSAGE_ID_KEY_ENVIRONMENT_VARIABLE));
    }

    private CfnTopicRule buildIotEventRule(String topicRuleName, Function lambda, String topicFilter) {
        CfnTopicRule.ActionProperty actionProperty = CfnTopicRule.ActionProperty.builder()
                .lambda(CfnTopicRule.LambdaActionProperty.builder()
                        .functionArn(lambda.getFunctionArn())
                        .build())
                .build();

        String quotedTopicFilter = "'" + topicFilter + "'";

        CfnTopicRule.TopicRulePayloadProperty topicRulePayloadProperty = CfnTopicRule.TopicRulePayloadProperty.builder()
                .actions(singletonList(actionProperty))
                .ruleDisabled(false)
                .sql(String.join(" ", DEFAULT_SELECT_CLAUSE, "from", quotedTopicFilter))
                .awsIotSqlVersion(AWS_IOT_SQL_VERSION)
                .build();

        CfnTopicRuleProps cfnTopicRuleProps = CfnTopicRuleProps.builder()
                .topicRulePayload(topicRulePayloadProperty)
                .build();

        return new CfnTopicRule(this, topicRuleName, cfnTopicRuleProps);
    }

    private CfnTopicRule buildIotEventRule(String topicRuleName, CfnTopicRule.RepublishActionProperty republishActionProperty, String sql) {
        CfnTopicRule.ActionProperty actionProperty = CfnTopicRule.ActionProperty.builder()
                .republish(republishActionProperty)
                .build();

        CfnTopicRule.TopicRulePayloadProperty topicRulePayloadProperty = CfnTopicRule.TopicRulePayloadProperty.builder()
                .actions(singletonList(actionProperty))
                .ruleDisabled(false)
                .sql(sql)
                .awsIotSqlVersion(AWS_IOT_SQL_VERSION)
                .build();

        CfnTopicRuleProps cfnTopicRuleProps = CfnTopicRuleProps.builder()
                .topicRulePayload(topicRulePayloadProperty)
                .build();

        return new CfnTopicRule(this, topicRuleName, cfnTopicRuleProps);
    }

    private Queue buildInboundMessageQueue() {
        log.warn("Environment variable [" + INBOUND_SQS_QUEUE_ARN_ENVIRONMENT_VARIABLE + "] not specified, using a default inbound SQS queue");

        QueueProps queueProps = QueueProps.builder()
                .visibilityTimeout(queueVisibilityTimeout)
                .build();
        return new Queue(this, "InboundSqsQueue", queueProps);
    }

    private Queue buildOutboundMessageQueue() {
        log.warn("Environment variable [" + OUTBOUND_SQS_QUEUE_ARN_ENVIRONMENT_VARIABLE + "] not specified, using a default outbound SQS queue");

        QueueProps queueProps = QueueProps.builder()
                .visibilityTimeout(queueVisibilityTimeout)
                .build();
        return new Queue(this, "OutboundSqsQueue", queueProps);
    }

    private EventSourceMapping buildEventSourceMapping(String sqsQueueArn, Function lambda) {
        EventSourceMappingProps eventSourceMappingProps = EventSourceMappingProps.builder()
                .eventSourceArn(sqsQueueArn)
                .batchSize(1)
                .target(lambda)
                .enabled(true)
                .build();

        return new EventSourceMapping(this, "MoveFromSqsToDynamoDbEventSourceMapping", eventSourceMappingProps);
    }

    private Map<String, String> getDefaultEnvironment(Table table) {
        Map<String, String> environment = new HashMap<>();
        environment.put(DYNAMO_DB_TABLE_ARN, table.getTableArn());

        return environment;
    }

    private Map<String, String> getGetMessageLambdaEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put(GET + REQUEST_TOPIC_PREFIX_KEY, GET_REQUEST_TOPIC_PREFIX);
        environment.put(GET + RESPONSE_TOPIC_PREFIX_KEY, GET_RESPONSE_TOPIC_PREFIX);

        return environment;
    }

    private Map<String, String> getQueryMessageLambdaEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put(QUERY + REQUEST_TOPIC_PREFIX_KEY, QUERY_REQUEST_TOPIC_PREFIX);
        environment.put(QUERY + RESPONSE_TOPIC_PREFIX_KEY, QUERY_RESPONSE_TOPIC_PREFIX);

        return environment;
    }

    private Map<String, String> getDeleteMessageLambdaEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put(DELETE + REQUEST_TOPIC_PREFIX_KEY, DELETE_REQUEST_TOPIC_PREFIX);
        environment.put(DELETE + RESPONSE_TOPIC_PREFIX_KEY, DELETE_RESPONSE_TOPIC_PREFIX);

        return environment;
    }

    private Map<String, String> getNextMessageLambdaEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put(NEXT + REQUEST_TOPIC_PREFIX_KEY, NEXT_REQUEST_TOPIC_PREFIX);
        environment.put(NEXT + RESPONSE_TOPIC_PREFIX_KEY, NEXT_RESPONSE_TOPIC_PREFIX);

        return environment;
    }

    private Map<String, String> getDevicesMessageLambdaEnvironment() {
        Map<String, String> environment = new HashMap<>();
        environment.put(DEVICES + REQUEST_TOPIC_PREFIX_KEY, DEVICES_REQUEST_TOPIC_PREFIX);
        environment.put(DEVICES + RESPONSE_TOPIC_PREFIX_KEY, DEVICES_RESPONSE_TOPIC_PREFIX);

        return environment;
    }

    private Map<String, String> getSendMessageLambdaEnvironment(String outboundSqsQueueArn) {
        Map<String, String> environment = new HashMap<>();
        environment.put(SEND + REQUEST_TOPIC_PREFIX_KEY, SEND_REQUEST_TOPIC_PREFIX);
        environment.put(SEND + RESPONSE_TOPIC_PREFIX_KEY, SEND_RESPONSE_TOPIC_PREFIX);
        environment.put(OUTBOUND_SQS_QUEUE_ARN, outboundSqsQueueArn);

        return environment;
    }

    private Function buildIotEventLambda(String functionName, Role role, Map<String, String> defaultEnvironment, String handlerClassName) {
        return buildIotEventLambda(functionName, role, defaultEnvironment, new HashMap<String, String>(), handlerClassName);
    }

    private Function buildIotEventLambda(String functionName, Role role, Map<String, String> defaultEnvironment, Map<String, String> additionalEnvironment, String handlerClassName) {
        Map<String, String> environment = new HashMap<>(defaultEnvironment);
        environment.putAll(additionalEnvironment);

        FunctionProps functionProps = FunctionProps.builder()
                .code(Code.fromAsset(String.join(NO_SEPARATOR, projectDirectory, buildOutputDirectory, outputJar)))
                .handler(String.join("::", handlerClassName, HANDLE_REQUEST))
                .memorySize(1024)
                .timeout(lambdaFunctionTimeout)
                .environment(environment)
                .runtime(Runtime.JAVA_8)
                .role(role)
                .tracing(Tracing.ACTIVE)
                .build();

        return new Function(this, functionName, functionProps);
    }

    private Function buildMoveFromSqsToDynamoDbLambda(String inboundSqsQueueArn, String uuidKey, String messageIdKey, Table table, Role role) {
        Map<String, String> environment = new HashMap<>();
        environment.put(INBOUND_SQS_QUEUE_ARN, inboundSqsQueueArn);
        environment.put(DYNAMO_DB_TABLE_ARN, table.getTableArn());
        environment.put(UUID_KEY, uuidKey);
        environment.put(MESSAGE_ID_KEY, messageIdKey);

        FunctionProps functionProps = FunctionProps.builder()
                .code(Code.fromAsset(String.join(NO_SEPARATOR, projectDirectory, buildOutputDirectory, outputJar)))
                .handler(String.join("::", SQS_EVENT_HANDLER, HANDLE_REQUEST))
                .memorySize(1024)
                .timeout(lambdaFunctionTimeout)
                .environment(environment)
                .runtime(Runtime.JAVA_8)
                .role(role)
                .tracing(Tracing.ACTIVE)
                .build();

        return new Function(this, "MoveFromSqsToDynamoDbLambda", functionProps);
    }

    private void buildJar(File projectDirectoryFile) {
        ProjectConnection projectConnection = GradleConnector.newConnector()
                .forProjectDirectory(projectDirectoryFile)
                .connect();

        Try.withResources(() -> projectConnection)
                .of(this::runBuild)
                .get();
    }

    private Void runBuild(ProjectConnection projectConnection) {
        // Build with gradle and send the output to stderr
        BuildLauncher build = projectConnection.newBuild();
        build.forTasks("build");
        build.setStandardOutput(System.err);
        build.run();

        return null;
    }

    private Role buildMoveFromSqsToDynamoDbRole(String queueArn, Table table) {
        PolicyStatement sqsPolicyStatement = LambdaPolicies.getMinimalLambdaSqsQueueEventSourceMappingTargetPolicy(queueArn);

        PolicyStatementProps dynamoDbPolicyStatementProps = PolicyStatementProps.builder()
                .effect(Effect.ALLOW)
                .resources(singletonList(table.getTableArn()))
                .actions(singletonList(Permissions.DYNAMODB_PUT_ITEM_PERMISSION))
                .build();
        PolicyStatement dynamoDbPolicyStatement = new PolicyStatement(dynamoDbPolicyStatementProps);

        PolicyStatement iotPolicyStatement = getPublishToTopicPrefixPolicyStatement("notification");

        PolicyDocumentProps policyDocumentProps = PolicyDocumentProps.builder()
                .statements(Arrays.asList(sqsPolicyStatement,
                        CloudWatchEventsPolicies.getMinimalCloudWatchEventsLoggingPolicy(),
                        dynamoDbPolicyStatement,
                        iotPolicyStatement))
                .build();
        PolicyDocument policyDocument = new PolicyDocument(policyDocumentProps);

        Map<String, PolicyDocument> policyDocuments = new HashMap<>();
        policyDocuments.put("root", policyDocument);

        RoleProps moveFromSqsToDynamoDbRoleProps = RoleProps.builder()
                .assumedBy(LambdaPolicies.LAMBDA_SERVICE_PRINCIPAL)
                .inlinePolicies(policyDocuments)
                .build();

        return new Role(this, "MoveFromSqsToDynamoDbRole", moveFromSqsToDynamoDbRoleProps);
    }

    private PolicyStatement getSqsSendMessagePolicyStatement(String queueArn) {
        PolicyStatementProps sqsPolicyStatementProps = PolicyStatementProps.builder()
                .effect(Effect.ALLOW)
                .resources(Collections.singletonList(queueArn))
                .actions(Arrays.asList("sqs:SendMessage", "sqs:GetQueueUrl"))
                .build();

        return new PolicyStatement(sqsPolicyStatementProps);
    }

    private Role buildIotEventRoleForTopicPrefix(String roleName, String topicPrefix, List<PolicyStatement> additionalPolicyStatements) {
        PolicyStatement iotPolicyStatement = getPublishToTopicPrefixPolicyStatement(topicPrefix);

        return buildRoleAssumedByLambda(this, roleName, combinePolicyStatements(additionalPolicyStatements, iotPolicyStatement));
    }

    @NotNull
    private PolicyStatement getPublishToTopicPrefixPolicyStatement(String topicPrefix) {
        PolicyStatementProps iotPolicyStatementProps = PolicyStatementProps.builder()
                .effect(Effect.ALLOW)
                .resources(singletonList(Fn.join(NO_SEPARATOR, Arrays.asList("arn:aws:iot:", getRegion(), ":", getAccount(), ":topic/", topicPrefix, "/*"))))
                .actions(singletonList(Permissions.IOT_PUBLISH_PERMISSION))
                .build();
        return new PolicyStatement(iotPolicyStatementProps);
    }

    private PolicyStatement getGetItemPolicyStatementForTable(Table table) {
        return getPolicyStatementForTable(table, Permissions.DYNAMODB_GET_ITEM_PERMISSION);
    }

    private PolicyStatement getQueryPolicyStatementForTable(Table table) {
        return getPolicyStatementForTable(table, Permissions.DYNAMODB_QUERY_PERMISSION);
    }

    private PolicyStatement getDeleteItemPolicyStatementForTable(Table table) {
        return getPolicyStatementForTable(table, Permissions.DYNAMODB_DELETE_ITEM_PERMISSION);
    }

    private PolicyStatement getNextItemPolicyStatementForTable(Table table) {
        return getPolicyStatementForTable(table, Permissions.DYNAMODB_QUERY_PERMISSION);
    }

    private PolicyStatement getDevicesPolicyStatementForTable(Table table) {
        return getPolicyStatementForTable(table, Permissions.DYNAMODB_SCAN_PERMISSION);
    }

    private PolicyStatement getPolicyStatementForTable(Table table, String action) {
        PolicyStatementProps dynamoDbPolicyStatementProps = PolicyStatementProps.builder()
                .effect(Effect.ALLOW)
                .resources(singletonList(table.getTableArn()))
                .actions(singletonList(action))
                .build();
        return new PolicyStatement(dynamoDbPolicyStatementProps);
    }

    private Table buildMessageTable() {
        Attribute uuid = Attribute.builder()
                .type(AttributeType.STRING)
                .name(UUID_DYNAMO_DB_COLUMN_NAME)
                .build();

        Attribute messageId = Attribute.builder()
                .type(AttributeType.STRING)
                .name(MESSAGE_ID_DYNAMO_DB_COLUMN_NAME)
                .build();

        TableProps tableProps = TableProps.builder()
                .partitionKey(uuid)
                .sortKey(messageId)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        return new Table(this, "DynamoDbTable", tableProps);
    }
}
