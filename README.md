# RecipesAndIngredientsUpdateLambda

## Overview

`RecipesAndIngredientsUpdateLambda` is an AWS Lambda function written in Java that manages recipe and ingredient updates for users of the FitMyMacros application. This Lambda retrieves user data from DynamoDB, checks the user's recipe history, and updates the recipe list and food ingredient quantities accordingly.

This function performs the following operations:
- Retrieves user data from the `FitMyMacros` DynamoDB table.
- Updates or inserts new recipes in the user's recipe history.
- Adjusts ingredient quantities based on the recipes provided.

## Features

- **AWS Lambda Handler**: Implements the `RequestHandler` interface, enabling the function to handle incoming events.
- **DynamoDB Integration**: Retrieves and updates user data in the DynamoDB table.
- **Custom Recipe Management**: Manages the user's last six recipes, adding new ones and removing the oldest when necessary.
- **Ingredient Quantity Handling**: Updates ingredient quantities in the user's food inventory based on the provided recipe ingredients.

## AWS Services Used

- **Lambda**: Executes the function in response to incoming events.
- **DynamoDB**: A NoSQL database service used to store and manage user data, including recipes and ingredients.

## Installation

### Prerequisites

- Java 8 or higher
- Maven (for building the project)
- AWS SDK for Java (included in the project dependencies)

### Build

To build the project, run the following Maven command:

mvn clean install

Deployment
Create the DynamoDB Table:

Ensure that a DynamoDB table named FitMyMacros exists in your AWS account with appropriate partition keys and indexes.
Deploy the Lambda:

Package the Lambda code into a .zip file or use AWS SAM/Serverless Framework.
Deploy the Lambda function via the AWS Management Console, AWS CLI, or an automated deployment tool.

Code Structure
handleRequest: The main entry point of the Lambda function. It processes the incoming event, retrieves user data, and updates recipes and ingredients in DynamoDB.
convertBodyToMap: Converts the JSON string body into a Java Map.
retrieveItemFromDynamoDB: Retrieves the user's data from DynamoDB using the userId.
updateItemInDynamoDB: Updates the user's recipe list and food inventory in DynamoDB.
updateFoodAvailability: Subtracts ingredient quantities from the user's food inventory based on the provided recipe.
extractUsedQuantity: Extracts numeric values from ingredient quantity strings.
