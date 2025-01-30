package com.fitmymacros.recipesandingredientsupdatelambda;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

public class App implements RequestHandler<Map<String, Object>, Object> {

    private static final String TABLE_NAME = "FitMyMacros";
    private static final int MAX_RECIPES = 6;
    private final DynamoDbClient dynamoDbClient;

    /**
     * Constructor initializes the DynamoDbClient.
     */
    public App() {
        this.dynamoDbClient = DynamoDbClient.builder().region(Region.EU_WEST_3).build();
    }

    /**
     * Handle the incoming request to update recipes and ingredients in DynamoDB.
     * 
     * @param input   The input map containing the body of the request.
     * @param context The context of the AWS Lambda function.
     * @return A success or error response.
     */
    @Override
    public Object handleRequest(Map<String, Object> input, Context context) {
        try {
            String bodyString = (String) input.get("body");
            Map<String, Object> body = convertBodyToMap(bodyString);
            String userId = (String) body.get("userId");
            Map<String, Object> recipe = (Map<String, Object>) body.get("recipe");

            String recipeName = (String) recipe.get("recipeName");
            Map<String, String> ingredientsAndQuantities = (Map<String, String>) recipe.get("ingredientsAndQuantities");

            Map<String, AttributeValue> item = retrieveItemFromDynamoDB(userId);
            List<AttributeValue> previousRecipes = item.get("previous_recipes") != null
                    ? item.get("previous_recipes").l()
                    : new ArrayList<>();
            List<AttributeValue> updatedPreviousRecipes = updatePreviousRecipes(previousRecipes, recipeName);

            Map<String, AttributeValue> food = item.get("food").m();
            updateItemInDynamoDB(userId, updatedPreviousRecipes, ingredientsAndQuantities, food);

            return buildSuccessResponse();
        } catch (Exception e) {
            return buildErrorResponse(e.getMessage());
        }
    }

    /**
     * Converts the body received as a String into a map.
     * 
     * @param body The request body as a String.
     * @return A mapped version of the request body.
     */
    private Map<String, Object> convertBodyToMap(String body) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(body, Map.class);
        } catch (IOException e) {
            throw new RuntimeException("Error parsing the request body.", e);
        }
    }

    /**
     * Retrieves the user item from DynamoDB.
     * 
     * @param userId The user ID whose data is to be retrieved.
     * @return The retrieved item as a map.
     */
    private Map<String, AttributeValue> retrieveItemFromDynamoDB(String userId) {
        GetItemRequest getItemRequest = GetItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("userId", AttributeValue.builder().s(userId).build()))
                .build();

        GetItemResponse getItemResponse = dynamoDbClient.getItem(getItemRequest);
        return getItemResponse.item();
    }

    /**
     * Updates the previous recipes list based on the number of recipes.
     * 
     * @param previousRecipes Existing previous recipes.
     * @param recipeName      The new recipe name.
     * @return The updated previous recipes list.
     */
    private List<AttributeValue> updatePreviousRecipes(List<AttributeValue> previousRecipes, String recipeName) {
        List<AttributeValue> updatedPreviousRecipes = new ArrayList<>(previousRecipes);

        if (updatedPreviousRecipes.size() >= MAX_RECIPES) {
            updatedPreviousRecipes.remove(0);
        }
        updatedPreviousRecipes.add(AttributeValue.builder().s(recipeName).build());

        return updatedPreviousRecipes;
    }

    /**
     * Updates the user item in DynamoDB with new recipes and food adjustments.
     * 
     * @param userId                  The user ID.
     * @param previousRecipes         The updated previous recipes list.
     * @param ingredientsAndQuantities The map of ingredients and their quantities.
     * @param food                    The map of the user's food data.
     */
    private void updateItemInDynamoDB(String userId, List<AttributeValue> previousRecipes, 
                                      Map<String, String> ingredientsAndQuantities, 
                                      Map<String, AttributeValue> food) {
        Map<String, AttributeValue> updatedFood = updateFoodAvailability(ingredientsAndQuantities, food);

        Map<String, String> expressionAttributeNames = Map.of("#PR", "previous_recipes", "#F", "food");
        Map<String, AttributeValue> expressionAttributeValues = Map.of(
                ":pr", AttributeValue.builder().l(previousRecipes).build(),
                ":f", AttributeValue.builder().m(updatedFood).build()
        );

        UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("userId", AttributeValue.builder().s(userId).build()))
                .updateExpression("SET #PR = :pr, #F = :f")
                .expressionAttributeNames(expressionAttributeNames)
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        dynamoDbClient.updateItem(updateItemRequest);
    }

    /**
     * Updates the food map by subtracting the quantities for the provided ingredients.
     * 
     * @param ingredientsAndQuantities The map of ingredients with their quantities.
     * @param food                     The map representing the food data.
     * @return The updated food map.
     */
    private Map<String, AttributeValue> updateFoodAvailability(Map<String, String> ingredientsAndQuantities,
            Map<String, AttributeValue> food) {
        Map<String, AttributeValue> updatedFood = new HashMap<>(food);

        for (Map.Entry<String, String> entry : ingredientsAndQuantities.entrySet()) {
            String ingredient = entry.getKey().replace("(dry)", "");
            String quantityString = entry.getValue();

            if (updatedFood.containsKey(ingredient)) {
                AttributeValue ingredientValue = updatedFood.get(ingredient);
                int requestedQuantity = Integer.parseInt(extractUsedQuantity(quantityString));
                int availableQuantity = Integer.parseInt(ingredientValue.n());

                int remainingQuantity = Math.max(0, availableQuantity - requestedQuantity);
                updatedFood.put(ingredient, AttributeValue.builder().n(Integer.toString(remainingQuantity)).build());
            }
        }
        return updatedFood;
    }

    /**
     * Extracts the numeric quantity from a given string.
     * 
     * @param quantityString The string containing the quantity.
     * @return The numeric portion of the quantity string.
     */
    private String extractUsedQuantity(String quantityString) {
        Matcher matcher = Pattern.compile("^\\d+").matcher(quantityString);
        return matcher.find() ? matcher.group() : "0";
    }

    /**
     * Builds a success response map.
     * 
     * @return The success response map.
     */
    private Map<String, Object> buildSuccessResponse() {
        return Map.of("statusCode", 200, "body", "Successfully invoked the lambda");
    }

    /**
     * Builds an error response with the given error message.
     * 
     * @param errorMessage The error message.
     * @return The error response as a string.
     */
    private String buildErrorResponse(String errorMessage) {
        System.out.println("Exception: " + errorMessage);
        return "Error occurred: " + errorMessage;
    }
}
