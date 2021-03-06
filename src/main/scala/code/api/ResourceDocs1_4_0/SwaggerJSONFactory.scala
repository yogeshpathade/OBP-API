package code.api.ResourceDocs1_4_0

import java.util.{Date, UUID}

import code.api.Constant._
import code.api.util.APIUtil
import code.api.util.APIUtil.ResourceDoc
import code.api.v2_2_0.BankJSON
import net.liftweb
import net.liftweb.json._
import net.liftweb.util.Props
import org.pegdown.PegDownProcessor

import scala.collection.immutable.ListMap
import scala.reflect.runtime.currentMirror
import scala.reflect.runtime.universe._

object SwaggerJSONFactory {
  //Info Object
  //link ->https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#infoObject
  case class InfoJson(
    title: String,
    description: String,
    contact: InfoContactJson,
    version: String
  )
  //Contact Object
  //https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#contactObject
  case class InfoContactJson(
    name: String,
    url: String,
    email: String
  )
  
  // Security Definitions Object
  // link->https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#securityDefinitionsObject
  case class SecurityDefinitionsJson(
    directLogin: DirectLoginJson
  )
  case class DirectLoginJson(
    `type`: String = "apiKey",
    description: String = "https://github.com/OpenBankProject/OBP-API/wiki/Direct-Login",
    in: String = "header",
    name: String = "Authorization"
  )
  
  //Security Requirement Object
  //link -> https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#securityRequirementObject
  case class SecurityJson(
    directLogin: List[String] = Nil
  )
  
  case class ResponseObjectSchemaJson(
    `$ref`: String
  )
  //Response Object 
  // links -> https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#responsesObject
  case class ResponseObjectJson(
    description: Option[String],
    schema: Option[ResponseObjectSchemaJson]
  )
  // Operation Object 
  // links -> https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#operation-object
  case class OperationObjectJson(
    tags: List[String],
    summary: String,
    security: List[SecurityJson] = SecurityJson()::Nil,
    description: String,
    operationId: String,
    parameters: List[OperationParameter],
    responses: Map[String, ResponseObjectJson]
  )
  //Parameter Object
  //link -> https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#parameterObject
  
  trait OperationParameter {
    def in: String
    def name: String
    def description: String
    def required: Boolean
  }
  case class OperationParameterPathJson (
    in: String = "path",
    name: String = "BANK_ID",
    description: String = "BANK_ID",
    required: Boolean = true,
    `type`: String ="string"
  )extends OperationParameter
  
  case class OperationParameterBodyJson (
    in: String = "body",
    name: String = "body",
    description: String = "BANK_BODY",
    required: Boolean = true,
    schema: ResponseObjectSchemaJson = ResponseObjectSchemaJson("#/definitions/BasicViewJSON")
  )extends OperationParameter
  
  case class ErrorPropertiesMessageJson(
    `type`: String
  )
  case class ErrorPropertiesCodeJson(
    `type`: String,
    format: String
  )
  case class ErrorPropertiesJson(
    code: ErrorPropertiesCodeJson,
    message: ErrorPropertiesMessageJson
  )
  case class ErrorDefinitionJson(
    `type`: String,
    required: List[String],
    properties: ErrorPropertiesJson
  )
  
  //in Swagger Definitions part, there are many sub-definitions, here just set the Error field.
  //other fields are set in "def loadDefinitions(resourceDocList: List[ResourceDoc])" method
  // Definitions Object
  // link ->https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#definitionsObject
  case class DefinitionsJson(Error: ErrorDefinitionJson)
  
  case class SwaggerResourceDoc(
    swagger: String,
    info: InfoJson,
    host: String,
    basePath: String,
    schemes: List[String],
    securityDefinitions: SecurityDefinitionsJson,
    security: List[SecurityJson],
    paths: Map[String, Map[String, OperationObjectJson]],
    definitions: DefinitionsJson
  )
  
  /**
    *Package the SwaggerResourceDoc with the ResourceDoc.
    * Note: the definitions of SwaggerResourceDoc only contains Error part,
    *       other specific OBP JSON part is filled by def "loadDefinitions(resourceDocList: List[ResourceDoc])"
    * case class ResourceDoc(
    *   partialFunction : PartialFunction[Req, Box[User] => Box[JsonResponse]],
    *   apiVersion: String, 
    *   apiFunction: String, 
    *   requestVerb: String, 
    *   requestUrl: String, 
    *   summary: String, 
    *   description: String, 
    *   exampleRequestBody: JValue, 
    *   successResponseBody: JValue, 
    *   errorResponseBodies: List[JValue], 
    *   catalogs: Catalogs,
    *   tags: List[ResourceDocTag]
    * )
    * 
    * -->
    * case class SwaggerResourceDoc(
    *   swagger: String,
    *   info: InfoJson,
    *   host: String,
    *   basePath: String,
    *   schemes: List[String],
    *   securityDefinitions: SecurityDefinitionsJson,
    *   security: List[SecurityJson],
    *   paths: Map[String, Map[String, OperationObjectJson]],
    *   definitions: DefinitionsJson
    * )
    *
    * @param resourceDocList     list of ResourceDoc
    * @param requestedApiVersion eg: 2_2_0
    * @return
    */
  def createSwaggerResourceDoc(resourceDocList: List[ResourceDoc], requestedApiVersion: String): SwaggerResourceDoc = {
    
    //reference to referenceObject: https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#referenceObject  
    //according to the apiFunction name, prepare the reference 
    // eg: set the following "$ref" field: 
    //    "path": "/banks/BANK_ID": {
    //      "get": {
    //      "responses": {
    //      "200": {
    //      "schema": {
    //         "$ref": "#/definitions/BankJSON"
    //TODO, try to make it work with reflection using rd.successResponseBody.extract[BanksJSON], but successResponseBody is JValue, that's tricky
    def setReferenceObject(rd: ResourceDoc) = {
      rd.apiFunction match {
        case "allAccountsAllBanks" => Some(ResponseObjectSchemaJson("#/definitions/BasicAccountJSON")) //1	V200/accounts
//        case "allAccountsAllBanks" => Some(ResponseObjectSchemaJson("#/definitions/AccountsJSON")) //1 V121 TODO 	/accounts
          
        case "corePrivateAccountsAllBanks" => Some(ResponseObjectSchemaJson("#/definitions/CoreAccountJSON")) //2	TODO List[CoreAccountJSON] /my/accounts
          
        case "allAccountsAtOneBank" => Some(ResponseObjectSchemaJson("#/definitions/BasicAccountJSON")) //3	V200 TODO List[BasicAccountJSON] /banks/BANK_ID/accounts
//        case "allAccountsAtOneBank" => Some(ResponseObjectSchemaJson("#/definitions/AccountsJSON")) //3	V121 TODO List[BasicAccountJSON] /banks/BANK_ID/accounts
          
          
        case "privateAccountsAtOneBank" => Some(ResponseObjectSchemaJson("#/definitions/BasicAccountsJSON")) //4	V200(used),V121 /banks/BANK_ID/accounts/private 
//        case "privateAccountsAtOneBank" => Some(ResponseObjectSchemaJson("#/definitions/AccountsJSON")) //4	V121 /banks/BANK_ID/accounts/private 
          
        case "getCoreAccountById" => Some(ResponseObjectSchemaJson("#/definitions/ModeratedCoreAccountJSON")) //5	V200 /my/banks/BANK_ID/accounts/ACCOUNT_ID/account
        case "accountById" => Some(ResponseObjectSchemaJson("#/definitions/ModeratedAccountJSON")) //6 v200 ,v121 /banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/account
        case "getCurrentUser" => Some(ResponseObjectSchemaJson("#/definitions/UserJSON")) //7	v200/users/current
        case "getBanks" => Some(ResponseObjectSchemaJson("#/definitions/BanksJSON"))//8	v121/banks
        case "bankById" => Some(ResponseObjectSchemaJson("#/definitions/BankJSON")) //9	v121 /banks/BANK_ID
        case "getCustomer" => Some(ResponseObjectSchemaJson("#/definitions/CustomerJson")) //10	V210 V140 /banks/BANK_ID/customer 
        case "getTransactionsForBankAccount" => Some(ResponseObjectSchemaJson("#/definitions/TransactionsJSON")) //11 V121	/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions
        case "getTransactionByIdForBankAccount" => Some(ResponseObjectSchemaJson("#/definitions/TransactionJSON")) //12	V121/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/transaction
        case "getCoreTransactionsForBankAccount" => Some(ResponseObjectSchemaJson("#/definitions/CoreTransactionsJSON"))//13	V200/my/banks/BANK_ID/accounts/ACCOUNT_ID/transactions
        case "getTransactionRequestTypes" => Some(ResponseObjectSchemaJson("#/definitions/TransactionRequestTypeJSONs"))//14	v140 /banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types
        case "createTransactionRequest" => Some(ResponseObjectSchemaJson("#/definitions/TransactionRequestWithChargeJSON210"))//15	v210, v200,v140/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/TRANSACTION_REQUEST_TYPE/transaction-requests
        case "answerTransactionRequestChallenge" => Some(ResponseObjectSchemaJson("#/definitions/TransactionRequestWithChargeJSON"))//16	v210, v200,v140 /banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/TRANSACTION_REQUEST_TYPE/transaction-requests/TRANSACTION_REQUEST_ID/challenge
        case "getTransactionRequests" => Some(ResponseObjectSchemaJson("#/definitions/TransactionRequestWithChargeJSONs210"))//17	/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-requests
        case "getCounterpartiesForAccount" => Some(ResponseObjectSchemaJson("#/definitions/CounterpartiesJSON"))//v220 18	/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties
        case "updateAccountLabel" => Some(ResponseObjectSchemaJson("#/definitions/SuccessMessage"))//19	/banks/BANK_ID/accounts/ACCOUNT_ID
        case "getViewsForBankAccount" => Some(ResponseObjectSchemaJson("#/definitions/ViewJSONV220"))//20	TODO V220 mixed V121 /banks/BANK_ID/accounts/ACCOUNT_ID/views
        case "createViewForBankAccount" => Some(ResponseObjectSchemaJson("#/definitions/ViewJSONV220"))//21	TODO V220 mixed V121 /banks/BANK_ID/accounts/ACCOUNT_ID/views
        case "updateViewForBankAccount" => Some(ResponseObjectSchemaJson("#/definitions/ViewJSONV220"))//22	TODO V220 mixed V121 /banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID
        case "deleteViewForBankAccount" => Some(ResponseObjectSchemaJson("#/definitions/SuccessMessage"))//23	TODO V220 mixed V121 /banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID
        case "addPermissionForUserForBankAccountForMultipleViews" => Some(ResponseObjectSchemaJson("#/definitions/ViewsJSON"))//24	/banks/BANK_ID/accounts/ACCOUNT_ID/permissions/PROVIDER_ID/USER_ID/views
        case "addPermissionForUserForBankAccountForOneView" => Some(ResponseObjectSchemaJson("#/definitions/ViewJSON"))//25	/banks/BANK_ID/accounts/ACCOUNT_ID/permissions/PROVIDER_ID/USER_ID/views/VIEW_ID
        case "removePermissionForUserForBankAccountForOneView" => Some(ResponseObjectSchemaJson("#/definitions/SuccessMessage"))//26	/banks/BANK_ID/accounts/ACCOUNT_ID/permissions/PROVIDER_ID/USER_ID/views/VIEW_ID
        case "removePermissionForUserForBankAccountForAllViews" => Some(ResponseObjectSchemaJson("#/definitions/SuccessMessage"))//27	/banks/BANK_ID/accounts/ACCOUNT_ID/permissions/PROVIDER_ID/USER_ID/views
        case "getCounterpartiesForAccount" => Some(ResponseObjectSchemaJson("#/definitions/CounterpartiesJSON"))//28	V220, V210 /banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/counterparties
        case "getOtherAccountsForBankAccount" => Some(ResponseObjectSchemaJson("#/definitions/OtherAccountsJSON"))//29	/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts
        case "getOtherAccountByIdForBankAccount" => Some(ResponseObjectSchemaJson("#/definitions/OtherAccountJSON"))//30	/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/other_accounts/OTHER_ACCOUNT_ID
        case "getTransactionNarrative" => Some(ResponseObjectSchemaJson("#/definitions/TransactionNarrativeJSON"))//31 /banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/narrative
        case "getCommentsForViewOnTransaction" => Some(ResponseObjectSchemaJson("#/definitions/TransactionCommentsJSON"))//32 /banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/comments
        case "deleteCommentForViewOnTransaction" => Some(ResponseObjectSchemaJson("#/definitions/SuccessMessage"))//33 TODO Wrong output for delete /banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/comments/COMMENT_ID
        case "getTagsForViewOnTransaction" => Some(ResponseObjectSchemaJson("#/definitions/TransactionTagsJSON"))//34	/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/tags
        case "deleteTagForViewOnTransaction" => Some(ResponseObjectSchemaJson("#/definitions/SuccessMessage"))//35 TODO Wrong output for delete /banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/tags/TAG_ID
        case "getImagesForViewOnTransaction" => Some(ResponseObjectSchemaJson("#/definitions/TransactionImagesJSON"))//36 /banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/images
        case "deleteImageForViewOnTransaction" => Some(ResponseObjectSchemaJson("#/definitions/SuccessMessage"))//37 TODO Wrong output for delete /banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/images/IMAGE_ID
        case "getWhereTagForViewOnTransaction" => Some(ResponseObjectSchemaJson("#/definitions/TransactionWhereJSON"))//38 /banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/metadata/where
        case "getOtherAccountForTransaction" => Some(ResponseObjectSchemaJson("#/definitions/OtherAccountJSON"))//39	/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions/TRANSACTION_ID/other_account
        case "getCurrentFxRate" => Some(ResponseObjectSchemaJson("#/definitions/FXRateJSON"))//40	/fx/FROM_CURRENCY_CODE/TO_CURRENCY_CODE
        case "getPermissionsForBankAccount" => Some(ResponseObjectSchemaJson("#/definitions/PermissionsJSON"))//41	V200 v121/banks/BANK_ID/accounts/ACCOUNT_ID/permissions
        case "getPermissionForUserForBankAccount" => Some(ResponseObjectSchemaJson("#/definitions/ViewsJSON"))//42	/banks/BANK_ID/accounts/ACCOUNT_ID/permissions/PROVIDER_ID/USER_ID
        case "createCustomer" => Some(ResponseObjectSchemaJson("#/definitions/CustomerJson"))//43	v210 v200 /banks/BANK_ID/customers
        case _ => None
      }
    }

    implicit val formats = DefaultFormats

    val pegDownProcessor : PegDownProcessor = new PegDownProcessor

    val infoTitle = "Open Bank Project API"
    val infoDescription = "An Open Source API for Banks. (c) TESOBE Ltd. 2011 - 2016. Licensed under the AGPL and commercial licences."
    val infoContact = InfoContactJson("TESOBE Ltd. / Open Bank Project", "https://openbankproject.com" ,"contact@tesobe.com")
    val infoApiVersion = requestedApiVersion
    val info = InfoJson(infoTitle, infoDescription, infoContact, infoApiVersion)
    val host = Props.get("hostname", "unknown host").replaceFirst("http://", "").replaceFirst("https://", "")
    val basePath = s"/$ApiPathZero/" + infoApiVersion
    val schemas = List("http", "https")
    // Paths Object
    // link ->https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#paths-object
    // setting up the following fileds of swagger json,eg apiFunction = bankById
    //  "paths": {
    //    "/banks/BANK_ID": --> mrd._1
    //      "get": {        --> all following from mrd._2
    //      "tags": [ "1_2_1"],
    //      "summary": "Get Bank",
    //      "description": "<p>Get the bank specified by BANK_ID....
    //      "operationId": "1_2_1-bankById",
    //      "responses": {
    //        "200": {
    //          "description": "Success",
    //          "schema": { "$ref": "#/definitions/BankJSON" }
    //        },
    //        "400": {
    //          "description": "Error",
    //          "schema": {"$ref": "#/definitions/Error"
    val paths: ListMap[String, Map[String, OperationObjectJson]] = resourceDocList.groupBy(x => x.requestUrl).toSeq.sortBy(x => x._1).map { mrd =>
      
      //TODO here can extract to  a method
      val path =
        mrd._1
        .replaceAll("/BANK_ID", "/{BANK_ID}")
        .replaceAll("/ACCOUNT_ID", "/{ACCOUNT_ID}")
        .replaceAll("/VIEW_ID", "/{VIEW_ID}")
        .replaceAll("/USER_ID", "/{USER_ID}")
        .replaceAll("/TRANSACTION_ID", "/{TRANSACTION_ID}")
        .replaceAll("/TRANSACTION_REQUEST_TYPE", "/{TRANSACTION_REQUEST_TYPE}")
        .replaceAll("/TRANSACTION_REQUEST_ID", "/{TRANSACTION_REQUEST_ID}")
        .replaceAll("/PROVIDER_ID", "/{PROVIDER_ID}")
        .replaceAll("/OTHER_ACCOUNT_ID", "/{OTHER_ACCOUNT_ID}")
        .replaceAll("/FROM_CURRENCY_CODE", "/{FROM_CURRENCY_CODE}")
        .replaceAll("/TO_CURRENCY_CODE", "/{TO_CURRENCY_CODE}")
        .replaceAll("/COMMENT_ID", "/{COMMENT_ID}")
        .replaceAll("/TAG_ID", "/{TAG_ID}")
        .replaceAll("/IMAGE_ID", "/{IMAGE_ID}")
        .replaceAll("/CUSTOMER_ID", "/{CUSTOMER_ID}")
        .replaceAll("/BRANCH_ID", "/{BRANCH_ID}")
        .replaceAll("/NEW_ACCOUNT_ID", "/{NEW_ACCOUNT_ID}")
        .replaceAll("/CONSUMER_ID", "/{CONSUMER_ID}")
        .replaceAll("/USER_EMAIL", "/{USER_EMAIL}")
        .replaceAll("/ENTITLEMENT_ID", "/{ENTITLEMENT_ID}")
        .replaceAll("/KYC_CHECK_ID", "/{KYC_CHECK_ID}")
        .replaceAll("/KYC_DOCUMENT_ID", "/{KYC_DOCUMENT_ID}")
        .replaceAll("/KYC_MEDIA_ID", "/{KYC_MEDIA_ID}")
        .replaceAll("/AMT_ID", "/{AMT_ID}")
      
      var pathParameters = List.empty[OperationParameter]
      if(path.contains("/{BANK_ID}"))
        pathParameters = OperationParameterPathJson(name="BANK_ID", description="The bank id") :: pathParameters
      if(path.contains("/{ACCOUNT_ID}"))
        pathParameters = OperationParameterPathJson(name="ACCOUNT_ID", description="The account id") :: pathParameters
      if(path.contains("/{VIEW_ID}"))
        pathParameters = OperationParameterPathJson(name="VIEW_ID", description="The view id") :: pathParameters
      if(path.contains("/{USER_ID}"))
        pathParameters = OperationParameterPathJson(name="USER_ID", description="The user id") :: pathParameters
      if(path.contains("/{TRANSACTION_ID}"))
        pathParameters = OperationParameterPathJson(name="TRANSACTION_ID", description="The transaction id") :: pathParameters
      if(path.contains("/{TRANSACTION_REQUEST_TYPE}"))
        pathParameters = OperationParameterPathJson(name="TRANSACTION_REQUEST_TYPE", description="The transaction request type") :: pathParameters
      if(path.contains("/{TRANSACTION_REQUEST_ID}"))
        pathParameters = OperationParameterPathJson(name="TRANSACTION_REQUEST_ID", description="The transaction request id") :: pathParameters
      if(path.contains("/{PROVIDER_ID}"))
        pathParameters = OperationParameterPathJson(name="PROVIDER_ID", description="The provider id") :: pathParameters
      if(path.contains("/{OTHER_ACCOUNT_ID}"))
        pathParameters = OperationParameterPathJson(name="OTHER_ACCOUNT_ID", description="The other account id") :: pathParameters
      if(path.contains("/{FROM_CURRENCY_CODE}"))
        pathParameters = OperationParameterPathJson(name="FROM_CURRENCY_CODE", description="The from currency code") :: pathParameters
      if(path.contains("/{TO_CURRENCY_CODE}"))
        pathParameters = OperationParameterPathJson(name="TO_CURRENCY_CODE", description="The to currency code") :: pathParameters
      if(path.contains("/{COMMENT_ID}"))
        pathParameters = OperationParameterPathJson(name="COMMENT_ID", description="The comment id") :: pathParameters
      if(path.contains("/{TAG_ID}"))
        pathParameters = OperationParameterPathJson(name="TAG_ID", description="The tag id") :: pathParameters
      if(path.contains("/{IMAGE_ID}"))
        pathParameters = OperationParameterPathJson(name="IMAGE_ID", description="The image id") :: pathParameters
      if(path.contains("/{CUSTOMER_ID}"))
        pathParameters = OperationParameterPathJson(name="CUSTOMER_ID", description="The customer id") :: pathParameters
      if(path.contains("/{BRANCH_ID}"))
        pathParameters = OperationParameterPathJson(name="BRANCH_ID", description="The branch id") :: pathParameters
      if(path.contains("/{NEW_ACCOUNT_ID}"))
        pathParameters = OperationParameterPathJson(name="NEW_ACCOUNT_ID", description="new account id") :: pathParameters
      if(path.contains("/{CONSUMER_ID}"))
        pathParameters = OperationParameterPathJson(name="CONSUMER_ID", description="new consumer id") :: pathParameters
      if(path.contains("/{USER_EMAIL}"))
        pathParameters = OperationParameterPathJson(name="USER_EMAIL", description="The user email id") :: pathParameters
      if(path.contains("/{ENTITLEMENT_ID}"))
        pathParameters = OperationParameterPathJson(name="ENTITLEMENT_ID", description="The entitblement id") :: pathParameters
      if(path.contains("/{KYC_CHECK_ID}"))
        pathParameters = OperationParameterPathJson(name="KYC_CHECK_ID", description="The kyc check id") :: pathParameters
      if(path.contains("/{KYC_DOCUMENT_ID}"))
        pathParameters = OperationParameterPathJson(name="KYC_DOCUMENT_ID", description="The kyc document id") :: pathParameters
      if(path.contains("/{KYC_MEDIA_ID}"))
        pathParameters = OperationParameterPathJson(name="KYC_MEDIA_ID", description="The kyc media id") :: pathParameters
      if(path.contains("/{AMT_ID}"))
        pathParameters = OperationParameterPathJson(name="AMT_ID", description="The kyc media id") :: pathParameters
  
      val operationObjects: Map[String, OperationObjectJson] = mrd._2.map(rd =>
        (rd.requestVerb.toLowerCase,
          OperationObjectJson(
            tags = List(s"${rd.apiVersion.toString}"), 
            summary = rd.summary,
            description = pegDownProcessor.markdownToHtml(rd.description.stripMargin).replaceAll("\n", ""),
            operationId =
              rd.apiFunction match {
                //TODO, the UUID is just a temporory way, need fix 
                case "createTransactionRequest" => s"${rd.apiVersion.toString }-${rd.apiFunction.toString}-${UUID.randomUUID().toString}"
                case _ => s"${rd.apiVersion.toString }-${rd.apiFunction.toString }"
              },
            parameters =
              rd.apiFunction match {
                case "createTransactionRequest" => OperationParameterBodyJson(schema=ResponseObjectSchemaJson("#/definitions/TransactionRequestBodyJSON")) :: pathParameters//15	v210, v200,v140/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/TRANSACTION_REQUEST_TYPE/transaction-requests
                case "answerTransactionRequestChallenge" => OperationParameterBodyJson(schema=ResponseObjectSchemaJson("#/definitions/ChallengeAnswerJSON")) :: pathParameters//16	v210, v200,v140 /banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/TRANSACTION_REQUEST_TYPE/transaction-requests/TRANSACTION_REQUEST_ID/challenge
                case "updateAccountLabel" => OperationParameterBodyJson(schema=ResponseObjectSchemaJson("#/definitions/UpdateAccountJSON")) :: pathParameters//19	/banks/BANK_ID/accounts/ACCOUNT_ID
                case "createViewForBankAccount" =>OperationParameterBodyJson(schema=ResponseObjectSchemaJson("#/definitions/CreateViewJSON")) :: pathParameters//21	TODO V220 mixed V121 /banks/BANK_ID/accounts/ACCOUNT_ID/views
                case "updateViewForBankAccount" => OperationParameterBodyJson(schema=ResponseObjectSchemaJson("#/definitions/UpdateViewJSON")) :: pathParameters//22	TODO V220 mixed V121 /banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID
                case "addPermissionForUserForBankAccountForMultipleViews" => OperationParameterBodyJson(schema=ResponseObjectSchemaJson("#/definitions/ViewIdsJson")) :: pathParameters//24	/banks/BANK_ID/accounts/ACCOUNT_ID/permissions/PROVIDER_ID/USER_ID/views
                case "createCustomer" => OperationParameterBodyJson(schema=ResponseObjectSchemaJson("#/definitions/PostCustomerJson")) :: pathParameters//43	v210 v200 /banks/BANK_ID/customers
                case _ => pathParameters
              },
            responses = Map("200" -> ResponseObjectJson(Some("Success"), setReferenceObject(rd)), 
                            "400" -> ResponseObjectJson(Some("Error"), Some(ResponseObjectSchemaJson("#/definitions/Error"))))))
      ).toMap
      (path, operationObjects.toSeq.sortBy(m => m._1).toMap)
    }(collection.breakOut)

    val errorRequired = List("code", "message")
    val errorPropertiesCode = ErrorPropertiesCodeJson("integer", "int32")
    val errorPropertiesMessage = ErrorPropertiesMessageJson("string")
    val errorProperties = ErrorPropertiesJson(errorPropertiesCode, errorPropertiesMessage)
    val errorDefinition = ErrorDefinitionJson("object", errorRequired, errorProperties)
    val definitions = DefinitionsJson(errorDefinition)

    SwaggerResourceDoc(
      swagger = "2.0",
      info = info,
      host = host,
      basePath = basePath,
      schemes = schemas,
      securityDefinitions = SecurityDefinitionsJson(DirectLoginJson()), //default value
      security = SecurityJson()::Nil, //default value
      paths = paths,
      definitions = definitions
    )
  }

  
  /**
    * @param entity - Any, maybe a case class, maybe a list ,maybe a string
    *               ExampleJSON (
    *               id = 5,
    *               name = "Tesobe",
    *               bank = Bank("gh.29.uk")
    *               banks = List(Bank("gh.29.uk"))
    *               )
    * @return - String, with Swagger format  
    *         "ExampleJSON":
    *         { 
    *           "required": ["id","name","bank","banks"],    
    *           "properties":
    *           { 
    *             "id": {"type":"integer", "format":"int32"}, 
    *             "Tesobe": {"type":"string"},
    *             "bank": {"$ref": "#/definitions/BankJSON"},
    *             "banks": {"type": "array", "items":{"$ref": "#/definitions/BanksJSON"}}
    *         }
    */
  def translateEntity(className:String, entity: Any): String = {
  
    //Collect all mandatory fields and make an appropriate string
    // eg return :  "required": ["id","name","bank","banks"],  
    val required =
      for {
        f <- entity.getClass.getDeclaredFields
        if f.getType.toString.contains("Option") == false
      } yield {
        f.getName
      }
    val requiredFields = required.toList mkString("[\"", "\",\"", "\"]")
    //Make part of mandatory fields
    val requiredFieldsPart = if (required.length > 0) """"required": """ + requiredFields + "," else ""
    //Make whole swagger definition of an entity
    
    //Get fields of runtime entities and put they into structure Map(nameOfField -> fieldAsObject)
    //eg: 
    //  ExampleJSON (                   
    //   id = 5,                         
    //   name = "Tesobe",                
    //   bank = Bank("gh.29.uk")        
    //   banks = List(Bank("gh.29.uk")) 
    //  )                               
    // -->
    //   mapOfFields = Map(
    //     id -> 5,
    //     name -> Tesobe,
    //     bank -> Bank(gh.29.uk),
    //     banks -> List(Bank(gh.29.uk))
    //   )
    //TODO this maybe not so useful now, the input is the case-classes now.
    val r = currentMirror.reflect(entity)
    val mapOfFields = r.symbol.typeSignature.members.toStream
      .collect { case s: TermSymbol if !s.isMethod => r.reflectField(s)}
      .map(r => r.symbol.name.toString.trim -> r.get)
      .toMap

    //Iterate over Map and use pattern matching to extract type of field of runtime entity and make an appropriate swagger Data Types for it
    //reference to https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#data-types
    // two pattern matching here: 
    //     Swagger Data Types, eg: (name -> Tesobe) Boolean --> "name": {"type":"string"}
    //     Specific OBP JSON Classes,(bank -> Bank(gh.29.uk)) --> "bank": {"$ref":"#/definitions/Bank"}
    // from up mapOfFields[String, Any]  --> List[String]
    //      id -> 5                      --> "id" : {"type":"integer", "format":"int32"}             
    //      name -> Tesobe,              --> "name" : {"type":"string"}              
    //      bank -> Bank(gh.29.uk),      --> "bank": {"$ref":"#/definitions/Bank"}    
    //      banks -> List(Bank(gh.29.uk) --> "banks": {"type": "array", "items":{"$ref": "#/definitions/Bank"}}  
    val properties = for ((key, value) <- mapOfFields) yield {
      value match {
        case i: Boolean                    => "\""  + key + """": {"type":"boolean", "example":"""" +i+"\"}"
        case Some(i: Boolean)              => "\""  + key + """": {"type":"boolean", "example":"""" +i+"\"}"
        case List(i: Boolean, _*)          => "\""  + key + """": {"type":"array", "items":{"type": "boolean"}}"""
        case Some(List(i: Boolean, _*))    => "\""  + key + """": {"type":"array", "items":{"type": "boolean"}}"""
        case i: String                     => "\""  + key + """": {"type":"string","example":"""" +i+"\"}"
        case Some(i: String)               => "\""  + key + """": {"type":"string","example":"""" +i+"\"}"
        case List(i: String, _*)           => "\""  + key + """": {"type":"array", "items":{"type": "string"}}"""
        case Some(List(i: String, _*))     => "\""  + key + """": {"type":"array", "items":{"type": "string"}}"""
        case i: Int                        => "\""  + key + """": {"type":"integer", "format":"int32","example":"""" +i+"\"}"
        case Some(i: Int)                  => "\""  + key + """": {"type":"integer", "format":"int32","example":"""" +i+"\"}"
        case List(i: Long, _*)             => "\""  + key + """": {"type":"array", "items":{"type":"integer", "format":"int32"}}"""
        case Some(List(i: Long, _*))       => "\""  + key + """": {"type":"array", "items":{"type":"integer", "format":"int32"}}"""
        case i: Long                       => "\""  + key + """": {"type":"integer", "format":"int64","example":"""" +i+"\"}"
        case Some(i: Long)                 => "\""  + key + """": {"type":"integer", "format":"int64","example":"""" +i+"\"}"
        case List(i: Long, _*)             => "\""  + key + """": {"type":"array", "items":{"type":"integer", "format":"int64"}}"""
        case Some(List(i: Long, _*))       => "\""  + key + """": {"type":"array", "items":{"type":"integer", "format":"int64"}}"""
        case i: Float                      => "\""  + key + """": {"type":"number", "format":"float","example":"""" +i+"\"}"
        case Some(i: Float)                => "\""  + key + """": {"type":"number", "format":"float","example":"""" +i+"\"}"
        case List(i: Float, _*)            => "\""  + key + """": {"type":"array", "items":{"type": "float"}}"""
        case Some(List(i: Float, _*))      => "\""  + key + """": {"type":"array", "items":{"type": "float"}}"""
        case i: Double                     => "\""  + key + """": {"type":"number", "format":"double","example":"""" +i+"\"}"
        case Some(i: Double)               => "\""  + key + """": {"type":"number", "format":"double","example":"""" +i+"\"}"
        case List(i: Double, _*)           => "\""  + key + """": {"type":"array", "items":{"type": "double"}}"""
        case Some(List(i: Double, _*))     => "\""  + key + """": {"type":"array", "items":{"type": "double"}}"""
        case i: Date                       => "\""  + key + """": {"type":"string", "format":"date","example":"""" +i+"\"}"
        case Some(i: Date)                 => "\""  + key + """": {"type":"string", "format":"date","example":"""" +i+"\"}"
        case List(i: Date, _*)             => "\""  + key + """": {"type":"array", "items":{"type":"string", "format":"date"}}"""
        case Some(List(i: Date, _*))       => "\""  + key + """": {"type":"array", "items":{"type":"string", "format":"date"}}"""
        //TODO this should be improved, matching the JValue,now just support the default value
        case APIUtil.defaultJValue                 => "\""  + key + """": {"type":"string","example":""}"""
        //the case classes.  
        case List(f)                        => "\""  + key + """": {"type": "array", "items":{"$ref": "#/definitions/""" +f.getClass.getSimpleName ++"\"}}"
        case Some(f)                              => "\""  + key + """": {"$ref":"#/definitions/""" +f.getClass.getSimpleName +"\"}"
        case f                              => "\""  + key + """": {"$ref":"#/definitions/""" +f.getClass.getSimpleName +"\"}"
        case _ => "unknown"
      }
    }
    //Exclude all unrecognised fields and make part of fields definition
    // add comment and filter unknow
    // fields --> "id" : {"type":"integer", "format":"int32"} ,"name" : {"type":"string"} ,"bank": {"$ref":"#/definitions/Bank"} ,"banks": {"type": "array", "items":{"$ref": "#/definitions/Bank"}}  
    val fields: String = properties filter (_.contains("unknown") == false) mkString (",")
    //val definition = "\"" + entity.getClass.getSimpleName + "\":{" + requiredFieldsPart + """"properties": {""" + fields + """}}"""
    val definition = "\"" + className + "\":{" +requiredFieldsPart+ """"properties": {""" + fields + """}}"""
    definition
  }
       
  /**
    * @param resourceDocList 
    * @return - JValue, with Swagger format, many following Strings
    *         {
    *         "definitions":{
    *           "ExampleJSON":
    *           { 
    *             "required": ["id","name","bank","banks"],    
    *             "properties":
    *             { 
    *               "id": {"type":"integer", "format":"int32"}, 
    *               "Tesobe": {"type":"string"},
    *               "bank": {"$ref": "#/definitions/BankJSON"},
    *               "banks": {"type": "array", "items":{"$ref": "#/definitions/BanksJSON"}
    *             }
    *           }
    *         } ...
    */
  def loadDefinitions(resourceDocList: List[ResourceDoc]): liftweb.json.JValue = {
  
    implicit val formats = DefaultFormats
  
    val allSwaggerDefinitionCaseClasses = SwaggerJSONsV220.allFieldsAndValues
    //Translate every entity(JSON Case Class) in a list to appropriate swagger format
    val listOfParticularDefinition =
      for (e <- allSwaggerDefinitionCaseClasses)
        yield {
          translateEntity(e._1, e._2)
        }
    //Add a comma between elements of a list and make a string 
    val particularDefinitionsPart = listOfParticularDefinition mkString (",")
    //Make a final string
    val definitions = "{\"definitions\":{" + particularDefinitionsPart + "}}"
    //Make a jsonAST from a string
    parse(definitions)
  }
 
}
