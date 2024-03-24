package com.fitmymacros.recipesandingredientsupdatelambda;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

public class App implements RequestHandler<Map<String, Object>, Object> {

    private DynamoDbClient dynamoDbClient;
    private final String TABLE_NAME = "FitMyMacros";

    public App() {
        this.dynamoDbClient = DynamoDbClient.builder().region(Region.EU_WEST_3).build();
    }

    /**
     * This lambda handler retrieves the data associated with an user in dynamoDB,
     * and if they
     * have more than 6 recipes in the last recipes list, it updates it with the
     * received recipes, and if not,
     * it inserts them
     */
    @Override
    public Object handleRequest(Map<String, Object> input, Context context) {
        try {
            System.out.println("input: " + input);
            String userId = (String) input.get("userId");
            List<String> lastRecipes = (List<String>) input.get("lastRecipes");

            Map<String, AttributeValue> item = retrieveItemFromDynamoDB(userId);
            System.out.println("item: " + item);
            List<AttributeValue> previousRecipes = item.get("previous_recipes").l();

            if (previousRecipes.size() < 6) {
                previousRecipes.addAll(convertRecipesToListAttributeValues(lastRecipes));
            } else {
                int numToRemove = lastRecipes.size();
                previousRecipes.subList(0, numToRemove).clear();
                previousRecipes.addAll(convertRecipesToListAttributeValues(lastRecipes));
            }

            updateItemInDynamoDB(userId, previousRecipes);

            return buildSuccessResponse();
        } catch (Exception e) {
            return this.buildErrorResponse(e.getMessage());
        }
    }

    /**
     * This method retrieves the whole item in dynamoDB for the given primary key
     * 
     * @param userId
     * @return
     */
    private Map<String, AttributeValue> retrieveItemFromDynamoDB(String userId) {
        GetItemRequest getItemRequest = GetItemRequest.builder()
                .tableName(this.TABLE_NAME)
                .key(Map.of("userId", AttributeValue.builder().s(userId).build()))
                .build();

        GetItemResponse getItemResponse = dynamoDbClient.getItem(getItemRequest);
        return getItemResponse.item();
    }

    private void updateItemInDynamoDB(String userId, List<AttributeValue> previousRecipes) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("userId", AttributeValue.builder().s(userId).build());
        item.put("previous_recipes", AttributeValue.builder().l(previousRecipes).build());

        PutItemRequest putItemRequest = PutItemRequest.builder()
                .tableName(TABLE_NAME)
                .item(item)
                .build();

        dynamoDbClient.putItem(putItemRequest);
    }

    private List<AttributeValue> convertRecipesToListAttributeValues(List<String> recipes) {
        return recipes.stream()
                .map(recipe -> AttributeValue.builder().s(recipe).build())
                .collect(Collectors.toList());
    }

    private Map<String, Object> buildSuccessResponse() {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("statusCode", 200);
        responseBody.put("body", "Successfully invoked the lambda asynchronously");
        return responseBody;
    }

    private String buildErrorResponse(String errorMessage) {
        return "Error occurred: " + errorMessage;
    }

}
