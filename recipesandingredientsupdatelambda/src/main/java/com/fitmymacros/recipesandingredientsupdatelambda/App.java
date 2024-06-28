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
            System.out.println("body: " + input.get("body").toString());
            Map<String, Object> body = this.convertBodyToMap(input.get("body").toString());
            String userId = (String) body.get("userId");
            Map<String, Object> recipe = (Map<String, Object>) body.get("recipe");
            System.out.println("recipe: " + recipe);

            String recipeName = recipe.get("recipeName").toString();
            System.out.println("recipeName: " + recipeName);
            Map<String, String> ingredientsAndQuantities = (Map<String, String>) recipe.get("ingredientsAndQuantities");

            Map<String, AttributeValue> item = retrieveItemFromDynamoDB(userId);
            System.out.println("item: " + item);
            List<AttributeValue> previousRecipes = item.get("previous_recipes") != null
                    ? item.get("previous_recipes").l()
                    : new ArrayList<>();
            List<AttributeValue> updatedPreviousRecipes = new ArrayList<>(previousRecipes);
            System.out.println("previous recipes: " + updatedPreviousRecipes);
            Map<String, AttributeValue> food = item.get("food").m();
            System.out.println("food: " + food);

            if (updatedPreviousRecipes != null && !updatedPreviousRecipes.isEmpty()
                    && updatedPreviousRecipes.size() < 6) {
                System.out.println("if 1: " + updatedPreviousRecipes);
                updatedPreviousRecipes.add(AttributeValue.builder().s(recipeName).build());
            } else if (updatedPreviousRecipes != null && !updatedPreviousRecipes.isEmpty()
                    && updatedPreviousRecipes.size() == 6) {
                System.out.println("if 2: " + updatedPreviousRecipes);
                List<AttributeValue> newList = new ArrayList<>(
                        updatedPreviousRecipes.subList(1, updatedPreviousRecipes.size()));
                newList.add(AttributeValue.builder().s(recipeName).build());
                updatedPreviousRecipes = newList;
            } else {
                System.out.println("if 3: " + updatedPreviousRecipes);
                updatedPreviousRecipes = new ArrayList<>();
                updatedPreviousRecipes.add(AttributeValue.builder().s(recipeName).build());
            }

            System.out.println("updated previous recipes: " + updatedPreviousRecipes);

            updateItemInDynamoDB(userId, updatedPreviousRecipes, ingredientsAndQuantities, food);

            return buildSuccessResponse();
        } catch (Exception e) {
            return this.buildErrorResponse(e.getMessage());
        }
    }

    /**
     * This method converts the body received as a String into a map
     * 
     * @param body
     * @return
     */
    private Map<String, Object> convertBodyToMap(String body) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(body, Map.class);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException();
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

    /**
     * This method updates the last recipes and the ingredients availability, for
     * the user
     * identified by userId in dynamoDB
     * 
     * @param userId
     * @param previousRecipes
     * @param ingredientsAndQuantities
     * @param food
     */
    private void updateItemInDynamoDB(String userId, List<AttributeValue> previousRecipes,
            Map<String, String> ingredientsAndQuantities, Map<String, AttributeValue> food) {
        Map<String, AttributeValue> updatedFood = this.updateFoodAvailability(ingredientsAndQuantities, food);
        System.out.println("updated food map: " + updatedFood);
        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#PR", "previous_recipes");
        expressionAttributeNames.put("#F", "food");

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":pr", AttributeValue.builder().l(previousRecipes).build());
        expressionAttributeValues.put(":f", AttributeValue.builder().m(updatedFood).build());

        UpdateItemRequest updateItemRequest = UpdateItemRequest.builder()
                .tableName(TABLE_NAME)
                .key(Map.of("userId", AttributeValue.builder().s(userId).build()))
                .updateExpression("SET #PR = :pr, #F = :f") // Update both previous_recipes and food attributes
                .expressionAttributeNames(expressionAttributeNames)
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        dynamoDbClient.updateItem(updateItemRequest);
    }

    /**
     * This method updates the food map, by substracting the quantites for the
     * ingredients that are provided in ingredientsAndQuantities
     * 
     * @param ingredientsAndQuantities
     * @param food
     * @return
     */
    private Map<String, AttributeValue> updateFoodAvailability(Map<String, String> ingredientsAndQuantities,
            Map<String, AttributeValue> food) {
        Map<String, AttributeValue> updatedFood = new HashMap<>(food);
        System.out.println("ingredientsAndQuantities: " + ingredientsAndQuantities);
        for (Map.Entry<String, String> entry : ingredientsAndQuantities.entrySet()) {
            System.out.println("entry: " + entry);
            String ingredient = entry.getKey();
            ingredient = ingredient.replace("(dry)", "");
            String quantityString = entry.getValue();

            if (updatedFood.containsKey(ingredient)) {
                System.out.println("food contains " + ingredient);
                AttributeValue ingredientValue = updatedFood.get(ingredient);
                String usedQuantityString = this.extractUsedQuantity(quantityString);
                System.out.println("used quantity for " + ingredient + ": " + usedQuantityString);

                int requestedQuantity = Integer.valueOf(usedQuantityString);

                // If the quantity is provided as grams or kilograms
                if (quantityString.endsWith("g") || quantityString.endsWith("kg")) {
                    System.out.println("updating quantity for " + ingredient);
                    System.out.println("ingredientvalue 1: " + ingredientValue);
                    int availableQuantity = Integer.valueOf(ingredientValue.s());
                    int remainingQuantity = availableQuantity - requestedQuantity;
                    updatedFood.put(ingredient,
                            AttributeValue.builder().n(Integer.toString(remainingQuantity)).build());
                    System.out.println("done updating quantity for " + ingredient);
                } else {
                    // If the quantity is provided as just a number (meaning units)
                    System.out.println("updating quantity for " + ingredient);
                    System.out.println("ingredientvalue 2: " + ingredientValue);
                    int availableQuantity = Integer.valueOf(ingredientValue.s());
                    int remainingQuantity = availableQuantity - requestedQuantity;
                    updatedFood.put(ingredient,
                            AttributeValue.builder().n(Integer.toString(remainingQuantity)).build());
                    System.out.println("done updating quantity for " + ingredient);
                }
            }
        }
        return updatedFood;
    }

    /**
     * This method recives a String that represents some quantity, as grams, or
     * units, and extracts number of it
     * 
     * @param quantityString
     * @return
     */
    private String extractUsedQuantity(String quantityString) {
        Pattern pattern = Pattern.compile("^\\d+");
        Matcher matcher = pattern.matcher(quantityString);
        if (matcher.find()) {
            return matcher.group();
        } else {
            System.out.println("No number found at the beginning of the string.");
            return "0";
        }
    }

    private Map<String, Object> buildSuccessResponse() {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("statusCode", 200);
        responseBody.put("body", "Successfully invoked the lambda");
        return responseBody;
    }

    private String buildErrorResponse(String errorMessage) {
        System.out.println("exception: " + errorMessage);
        return "Error occurred: " + errorMessage;
    }

}
