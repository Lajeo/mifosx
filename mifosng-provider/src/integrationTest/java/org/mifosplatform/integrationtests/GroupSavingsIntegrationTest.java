/**
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.mifosplatform.integrationtests;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import org.joda.time.LocalDate;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mifosplatform.integrationtests.common.ClientHelper;
import org.mifosplatform.integrationtests.common.CommonConstants;
import org.mifosplatform.integrationtests.common.GroupHelper;
import org.mifosplatform.integrationtests.common.Utils;
import org.mifosplatform.integrationtests.common.charges.ChargesHelper;
import org.mifosplatform.integrationtests.common.savings.SavingsAccountHelper;
import org.mifosplatform.integrationtests.common.savings.SavingsProductHelper;
import org.mifosplatform.integrationtests.common.savings.SavingsStatusChecker;

import com.jayway.restassured.builder.RequestSpecBuilder;
import com.jayway.restassured.builder.ResponseSpecBuilder;
import com.jayway.restassured.http.ContentType;
import com.jayway.restassured.specification.RequestSpecification;
import com.jayway.restassured.specification.ResponseSpecification;

/**
 * Group Savings Integration Test for checking Savings Application.
 */
@SuppressWarnings({ "rawtypes", "unused" })
public class GroupSavingsIntegrationTest {

    public static final String DEPOSIT_AMOUNT = "2000";
    public static final String WITHDRAW_AMOUNT = "1000";
    public static final String WITHDRAW_AMOUNT_ADJUSTED = "500";
    public static final String MINIMUM_OPENING_BALANCE = "1000.0";
    public static final String ACCOUNT_TYPE_INDIVIDUAL = "INDIVIDUAL";
    public static final String ACCOUNT_TYPE_GROUP = "GROUP";
    public static final String ACCOUNT_TYPE_JLG = "JLG";
    private final String CALENDAR_START_DATE = "06 July 2014";

    private ResponseSpecification responseSpec;
    private RequestSpecification requestSpec;
    private SavingsAccountHelper savingsAccountHelper;

    @Before
    public void setup() {
        Utils.initializeRESTAssured();
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
        this.responseSpec = new ResponseSpecBuilder().expectStatusCode(200).build();
    }

    @Test
    public void testSavingsAccount() {
        this.savingsAccountHelper = new SavingsAccountHelper(requestSpec, responseSpec);

        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        Assert.assertNotNull(clientID);

        Integer groupId = GroupHelper.createGroup(this.requestSpec, this.responseSpec, true);
        Assert.assertNotNull(groupId);

        groupId = GroupHelper.associateClient(this.requestSpec, this.responseSpec, groupId.toString(), clientID.toString());
        Assert.assertNotNull(groupId);

        final String minBalanceForInterestCalculation = null;
        final String minRequiredBalance = null;
        final String enforceMinRequiredBalance = "false";
        final Integer savingsProductID = createSavingsProduct(this.requestSpec, this.responseSpec, MINIMUM_OPENING_BALANCE,
                minBalanceForInterestCalculation, minRequiredBalance, enforceMinRequiredBalance);
        Assert.assertNotNull(savingsProductID);

        final Integer savingsId = this.savingsAccountHelper.applyForSavingsApplication(groupId, groupId, savingsProductID,
                ACCOUNT_TYPE_GROUP);
        Assert.assertNotNull(savingsId);

        HashMap modifications = this.savingsAccountHelper.updateSavingsAccount(groupId, groupId, savingsProductID, savingsId,
                ACCOUNT_TYPE_GROUP);
        Assert.assertTrue(modifications.containsKey("submittedOnDate"));

        HashMap savingsStatusHashMap = SavingsStatusChecker.getStatusOfSavings(this.requestSpec, this.responseSpec, savingsId);
        SavingsStatusChecker.verifySavingsIsPending(savingsStatusHashMap);

        savingsStatusHashMap = this.savingsAccountHelper.approveSavings(savingsId);
        SavingsStatusChecker.verifySavingsIsApproved(savingsStatusHashMap);

        savingsStatusHashMap = this.savingsAccountHelper.activateSavings(savingsId);
        SavingsStatusChecker.verifySavingsIsActive(savingsStatusHashMap);

        final HashMap summaryBefore = this.savingsAccountHelper.getSavingsSummary(savingsId);
        this.savingsAccountHelper.calculateInterestForSavings(savingsId);
        HashMap summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals(summaryBefore, summary);

        this.savingsAccountHelper.postInterestForSavings(savingsId);
        summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        Assert.assertFalse(summaryBefore.equals(summary));

        final Object savingsInterest = this.savingsAccountHelper.getSavingsInterest(savingsId);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSavingsAccount_CLOSE_APPLICATION() {
        this.savingsAccountHelper = new SavingsAccountHelper(this.requestSpec, this.responseSpec);
        final ResponseSpecification errorResponse = new ResponseSpecBuilder().expectStatusCode(400).build();
        final SavingsAccountHelper validationErrorHelper = new SavingsAccountHelper(this.requestSpec, errorResponse);

        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        Assert.assertNotNull(clientID);

        Integer groupId = GroupHelper.createGroup(this.requestSpec, this.responseSpec, true);
        Assert.assertNotNull(groupId);

        groupId = GroupHelper.associateClient(this.requestSpec, this.responseSpec, groupId.toString(), clientID.toString());
        Assert.assertNotNull(groupId);

        final String minBalanceForInterestCalculation = null;
        final String minRequiredBalance = "1000.0";
        final String enforceMinRequiredBalance = "true";
        final Integer savingsProductID = createSavingsProduct(this.requestSpec, this.responseSpec, MINIMUM_OPENING_BALANCE,
                minBalanceForInterestCalculation, minRequiredBalance, enforceMinRequiredBalance);
        Assert.assertNotNull(savingsProductID);

        final Integer savingsId = this.savingsAccountHelper.applyForSavingsApplication(groupId, groupId, savingsProductID,
                ACCOUNT_TYPE_GROUP);
        Assert.assertNotNull(savingsId);

        HashMap savingsStatusHashMap = SavingsStatusChecker.getStatusOfSavings(this.requestSpec, this.responseSpec, savingsId);
        SavingsStatusChecker.verifySavingsIsPending(savingsStatusHashMap);

        savingsStatusHashMap = this.savingsAccountHelper.approveSavings(savingsId);
        SavingsStatusChecker.verifySavingsIsApproved(savingsStatusHashMap);

        savingsStatusHashMap = this.savingsAccountHelper.activateSavings(savingsId);
        SavingsStatusChecker.verifySavingsIsActive(savingsStatusHashMap);

        DateFormat dateFormat = new SimpleDateFormat("dd MMMM yyyy", Locale.US);
        Calendar todaysDate = Calendar.getInstance();
        final String CLOSEDON_DATE = dateFormat.format(todaysDate.getTime());
        String withdrawBalance = "false";
        ArrayList<HashMap> savingsAccountErrorData = (ArrayList<HashMap>) validationErrorHelper.closeSavingsAccountAndGetBackRequiredField(
                savingsId, withdrawBalance, CommonConstants.RESPONSE_ERROR, CLOSEDON_DATE);
        assertEquals("validation.msg.savingsaccount.close.results.in.balance.not.zero",
                savingsAccountErrorData.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));

        withdrawBalance = "true";
        savingsStatusHashMap = this.savingsAccountHelper.closeSavingsAccount(savingsId, withdrawBalance);
        SavingsStatusChecker.verifySavingsAccountIsClosed(savingsStatusHashMap);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSavingsAccount_DELETE_APPLICATION() {
        this.savingsAccountHelper = new SavingsAccountHelper(this.requestSpec, this.responseSpec);

        SavingsAccountHelper savingsAccountHelperValidationError = new SavingsAccountHelper(this.requestSpec,
                new ResponseSpecBuilder().build());

        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        Assert.assertNotNull(clientID);

        Integer groupId = GroupHelper.createGroup(this.requestSpec, this.responseSpec, true);
        Assert.assertNotNull(groupId);

        groupId = GroupHelper.associateClient(this.requestSpec, this.responseSpec, groupId.toString(), clientID.toString());
        Assert.assertNotNull(groupId);

        final String minBalanceForInterestCalculation = null;
        final String minRequiredBalance = null;
        final String enforceMinRequiredBalance = "false";
        final Integer savingsProductID = createSavingsProduct(this.requestSpec, this.responseSpec, MINIMUM_OPENING_BALANCE,
                minBalanceForInterestCalculation, minRequiredBalance, enforceMinRequiredBalance);
        Assert.assertNotNull(savingsProductID);

        final Integer savingsId = this.savingsAccountHelper.applyForSavingsApplication(groupId, groupId, savingsProductID,
                ACCOUNT_TYPE_GROUP);
        Assert.assertNotNull(savingsId);

        HashMap savingsStatusHashMap = SavingsStatusChecker.getStatusOfSavings(this.requestSpec, this.responseSpec, savingsId);
        SavingsStatusChecker.verifySavingsIsPending(savingsStatusHashMap);

        savingsStatusHashMap = this.savingsAccountHelper.approveSavings(savingsId);
        SavingsStatusChecker.verifySavingsIsApproved(savingsStatusHashMap);

        List<HashMap> error1 = (List<HashMap>) savingsAccountHelperValidationError.deleteSavingsApplication(savingsId,
                CommonConstants.RESPONSE_ERROR);
        assertEquals("validation.msg.savingsaccount.delete.not.in.submittedandpendingapproval.state",
                error1.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));

        savingsStatusHashMap = this.savingsAccountHelper.undoApproval(savingsId);
        SavingsStatusChecker.verifySavingsIsPending(savingsStatusHashMap);

        this.savingsAccountHelper.deleteSavingsApplication(savingsId, CommonConstants.RESPONSE_RESOURCE_ID);

        List<HashMap> error = savingsAccountHelperValidationError.getSavingsCollectionAttribute(savingsId, CommonConstants.RESPONSE_ERROR);
        assertEquals("error.msg.saving.account.id.invalid", error.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));

    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSavingsAccount_REJECT_APPLICATION() {

        this.savingsAccountHelper = new SavingsAccountHelper(this.requestSpec, this.responseSpec);

        SavingsAccountHelper savingsAccountHelperValidationError = new SavingsAccountHelper(this.requestSpec,
                new ResponseSpecBuilder().build());

        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        Assert.assertNotNull(clientID);

        Integer groupId = GroupHelper.createGroup(this.requestSpec, this.responseSpec, true);
        Assert.assertNotNull(groupId);

        groupId = GroupHelper.associateClient(this.requestSpec, this.responseSpec, groupId.toString(), clientID.toString());
        Assert.assertNotNull(groupId);

        final String minBalanceForInterestCalculation = null;
        final String minRequiredBalance = null;
        final String enforceMinRequiredBalance = "false";
        final Integer savingsProductID = createSavingsProduct(this.requestSpec, this.responseSpec, MINIMUM_OPENING_BALANCE,
                minBalanceForInterestCalculation, minRequiredBalance, enforceMinRequiredBalance);
        Assert.assertNotNull(savingsProductID);

        final Integer savingsId = this.savingsAccountHelper.applyForSavingsApplication(groupId, groupId, savingsProductID,
                ACCOUNT_TYPE_GROUP);
        Assert.assertNotNull(savingsId);

        HashMap savingsStatusHashMap = SavingsStatusChecker.getStatusOfSavings(this.requestSpec, this.responseSpec, savingsId);
        SavingsStatusChecker.verifySavingsIsPending(savingsStatusHashMap);

        savingsStatusHashMap = this.savingsAccountHelper.approveSavings(savingsId);
        SavingsStatusChecker.verifySavingsIsApproved(savingsStatusHashMap);

        List<HashMap> error1 = savingsAccountHelperValidationError.rejectApplicationWithErrorCode(savingsId,
                SavingsAccountHelper.CREATED_DATE_PLUS_ONE);
        assertEquals("validation.msg.savingsaccount.reject.not.in.submittedandpendingapproval.state",
                error1.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));

        savingsStatusHashMap = this.savingsAccountHelper.undoApproval(savingsId);
        SavingsStatusChecker.verifySavingsIsPending(savingsStatusHashMap);

        error1 = savingsAccountHelperValidationError.rejectApplicationWithErrorCode(savingsId, SavingsAccountHelper.getFutureDate());
        assertEquals("validation.msg.savingsaccount.reject.cannot.be.a.future.date",
                error1.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));

        error1 = savingsAccountHelperValidationError.rejectApplicationWithErrorCode(savingsId, SavingsAccountHelper.CREATED_DATE_MINUS_ONE);
        assertEquals("validation.msg.savingsaccount.reject.cannot.be.before.submittal.date",
                error1.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));

        savingsStatusHashMap = this.savingsAccountHelper.rejectApplication(savingsId);
        SavingsStatusChecker.verifySavingsIsRejected(savingsStatusHashMap);
    }

    @Test
    public void testSavingsAccount_WITHDRAW_APPLICATION() {

        this.savingsAccountHelper = new SavingsAccountHelper(this.requestSpec, this.responseSpec);

        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        Assert.assertNotNull(clientID);

        Integer groupId = GroupHelper.createGroup(this.requestSpec, this.responseSpec, true);
        Assert.assertNotNull(groupId);

        groupId = GroupHelper.associateClient(this.requestSpec, this.responseSpec, groupId.toString(), clientID.toString());
        Assert.assertNotNull(groupId);

        final String minBalanceForInterestCalculation = null;
        final String minRequiredBalance = null;
        final String enforceMinRequiredBalance = "false";
        final Integer savingsProductID = createSavingsProduct(this.requestSpec, this.responseSpec, MINIMUM_OPENING_BALANCE,
                minBalanceForInterestCalculation, minRequiredBalance, enforceMinRequiredBalance);
        Assert.assertNotNull(savingsProductID);

        final Integer savingsId = this.savingsAccountHelper.applyForSavingsApplication(groupId, groupId, savingsProductID,
                ACCOUNT_TYPE_GROUP);
        Assert.assertNotNull(savingsId);

        HashMap savingsStatusHashMap = SavingsStatusChecker.getStatusOfSavings(this.requestSpec, this.responseSpec, savingsId);
        SavingsStatusChecker.verifySavingsIsPending(savingsStatusHashMap);

        savingsStatusHashMap = this.savingsAccountHelper.withdrawApplication(savingsId);
        SavingsStatusChecker.verifySavingsIsWithdrawn(savingsStatusHashMap);

    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSavingsAccountTransactions() {
        this.savingsAccountHelper = new SavingsAccountHelper(this.requestSpec, this.responseSpec);
        SavingsAccountHelper savingsAccountHelperValidationError = new SavingsAccountHelper(this.requestSpec,
                new ResponseSpecBuilder().build());

        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        Assert.assertNotNull(clientID);

        Integer groupId = GroupHelper.createGroup(this.requestSpec, this.responseSpec, true);
        Assert.assertNotNull(groupId);

        groupId = GroupHelper.associateClient(this.requestSpec, this.responseSpec, groupId.toString(), clientID.toString());
        Assert.assertNotNull(groupId);

        final String minBalanceForInterestCalculation = null;
        final String minRequiredBalance = null;
        final String enforceMinRequiredBalance = "false";
        final Integer savingsProductID = createSavingsProduct(this.requestSpec, this.responseSpec, MINIMUM_OPENING_BALANCE,
                minBalanceForInterestCalculation, minRequiredBalance, enforceMinRequiredBalance);
        Assert.assertNotNull(savingsProductID);

        final Integer savingsId = this.savingsAccountHelper.applyForSavingsApplication(groupId, groupId, savingsProductID,
                ACCOUNT_TYPE_GROUP);
        Assert.assertNotNull(savingsId);

        HashMap savingsStatusHashMap = SavingsStatusChecker.getStatusOfSavings(this.requestSpec, this.responseSpec, savingsId);
        SavingsStatusChecker.verifySavingsIsPending(savingsStatusHashMap);

        savingsStatusHashMap = this.savingsAccountHelper.approveSavings(savingsId);
        SavingsStatusChecker.verifySavingsIsApproved(savingsStatusHashMap);

        List<HashMap> error = (List) savingsAccountHelperValidationError.withdrawalFromSavingsAccount(savingsId, "100",
                SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_ERROR);
        assertEquals("error.msg.savingsaccount.transaction.account.is.not.active",
                error.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));

        error = (List) savingsAccountHelperValidationError.depositToSavingsAccount(savingsId, "100", SavingsAccountHelper.TRANSACTION_DATE,
                CommonConstants.RESPONSE_ERROR);
        assertEquals("error.msg.savingsaccount.transaction.account.is.not.active",
                error.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));

        savingsStatusHashMap = this.savingsAccountHelper.activateSavings(savingsId);
        SavingsStatusChecker.verifySavingsIsActive(savingsStatusHashMap);

        HashMap summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        Float balance = new Float(MINIMUM_OPENING_BALANCE);
        assertEquals("Verifying opening Balance", balance, summary.get("accountBalance"));

        Integer depositTransactionId = (Integer) this.savingsAccountHelper.depositToSavingsAccount(savingsId, DEPOSIT_AMOUNT,
                SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);
        HashMap depositTransaction = this.savingsAccountHelper.getSavingsTransaction(savingsId, depositTransactionId);
        balance += new Float(DEPOSIT_AMOUNT);
        assertEquals("Verifying Deposit Amount", new Float(DEPOSIT_AMOUNT), depositTransaction.get("amount"));
        assertEquals("Verifying Balance after Deposit", balance, depositTransaction.get("runningBalance"));

        Integer withdrawTransactionId = (Integer) this.savingsAccountHelper.withdrawalFromSavingsAccount(savingsId, WITHDRAW_AMOUNT,
                SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);
        HashMap withdrawTransaction = this.savingsAccountHelper.getSavingsTransaction(savingsId, withdrawTransactionId);
        balance -= new Float(WITHDRAW_AMOUNT);
        assertEquals("Verifying Withdrawal Amount", new Float(WITHDRAW_AMOUNT), withdrawTransaction.get("amount"));
        assertEquals("Verifying Balance after Withdrawal", balance, withdrawTransaction.get("runningBalance"));

        Integer newWithdrawTransactionId = this.savingsAccountHelper.updateSavingsAccountTransaction(savingsId, withdrawTransactionId,
                WITHDRAW_AMOUNT_ADJUSTED);
        HashMap newWithdrawTransaction = this.savingsAccountHelper.getSavingsTransaction(savingsId, newWithdrawTransactionId);
        balance = balance + new Float(WITHDRAW_AMOUNT) - new Float(WITHDRAW_AMOUNT_ADJUSTED);
        assertEquals("Verifying adjusted Amount", new Float(WITHDRAW_AMOUNT_ADJUSTED), newWithdrawTransaction.get("amount"));
        assertEquals("Verifying Balance after adjust", balance, newWithdrawTransaction.get("runningBalance"));
        summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        assertEquals("Verifying Adjusted Balance", balance, summary.get("accountBalance"));
        withdrawTransaction = this.savingsAccountHelper.getSavingsTransaction(savingsId, withdrawTransactionId);
        Assert.assertTrue((Boolean) withdrawTransaction.get("reversed"));

        this.savingsAccountHelper.undoSavingsAccountTransaction(savingsId, newWithdrawTransactionId);
        newWithdrawTransaction = this.savingsAccountHelper.getSavingsTransaction(savingsId, withdrawTransactionId);
        Assert.assertTrue((Boolean) newWithdrawTransaction.get("reversed"));
        summary = this.savingsAccountHelper.getSavingsSummary(savingsId);
        balance += new Float(WITHDRAW_AMOUNT_ADJUSTED);
        assertEquals("Verifying Balance After Undo Transaction", balance, summary.get("accountBalance"));

        error = (List) savingsAccountHelperValidationError.withdrawalFromSavingsAccount(savingsId, "5000",
                SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_ERROR);
        assertEquals("error.msg.savingsaccount.transaction.insufficient.account.balance",
                error.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));

        error = (List) savingsAccountHelperValidationError.withdrawalFromSavingsAccount(savingsId, "5000",
                SavingsAccountHelper.getFutureDate(), CommonConstants.RESPONSE_ERROR);
        assertEquals("error.msg.savingsaccount.transaction.in.the.future", error.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));

        error = (List) savingsAccountHelperValidationError.depositToSavingsAccount(savingsId, "5000", SavingsAccountHelper.getFutureDate(),
                CommonConstants.RESPONSE_ERROR);
        assertEquals("error.msg.savingsaccount.transaction.in.the.future", error.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));

        error = (List) savingsAccountHelperValidationError.withdrawalFromSavingsAccount(savingsId, "5000",
                SavingsAccountHelper.CREATED_DATE_MINUS_ONE, CommonConstants.RESPONSE_ERROR);
        assertEquals("error.msg.savingsaccount.transaction.before.activation.date",
                error.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));

        error = (List) savingsAccountHelperValidationError.depositToSavingsAccount(savingsId, "5000",
                SavingsAccountHelper.CREATED_DATE_MINUS_ONE, CommonConstants.RESPONSE_ERROR);
        assertEquals("error.msg.savingsaccount.transaction.before.activation.date",
                error.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));

    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSavingsAccountCharges() {
        this.savingsAccountHelper = new SavingsAccountHelper(this.requestSpec, this.responseSpec);

        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        Assert.assertNotNull(clientID);

        Integer groupId = GroupHelper.createGroup(this.requestSpec, this.responseSpec, true);
        Assert.assertNotNull(groupId);

        groupId = GroupHelper.associateClient(this.requestSpec, this.responseSpec, groupId.toString(), clientID.toString());
        Assert.assertNotNull(groupId);

        final String minBalanceForInterestCalculation = null;
        final String minRequiredBalance = null;
        final String enforceMinRequiredBalance = "false";
        final Integer savingsProductID = createSavingsProduct(this.requestSpec, this.responseSpec, MINIMUM_OPENING_BALANCE,
                minBalanceForInterestCalculation, minRequiredBalance, enforceMinRequiredBalance);
        Assert.assertNotNull(savingsProductID);

        final Integer savingsId = this.savingsAccountHelper.applyForSavingsApplication(groupId, groupId, savingsProductID,
                ACCOUNT_TYPE_GROUP);
        Assert.assertNotNull(savingsId);

        HashMap savingsStatusHashMap = SavingsStatusChecker.getStatusOfSavings(this.requestSpec, this.responseSpec, savingsId);
        SavingsStatusChecker.verifySavingsIsPending(savingsStatusHashMap);

        final Integer withdrawalChargeId = ChargesHelper.createCharges(this.requestSpec, this.responseSpec,
                ChargesHelper.getSavingsWithdrawalFeeJSON());
        Assert.assertNotNull(withdrawalChargeId);

        this.savingsAccountHelper.addChargesForSavings(savingsId, withdrawalChargeId);
        ArrayList<HashMap> chargesPendingState = this.savingsAccountHelper.getSavingsCharges(savingsId);
        Assert.assertEquals(1, chargesPendingState.size());

        Integer savingsChargeId = (Integer) chargesPendingState.get(0).get("id");
        HashMap chargeChanges = this.savingsAccountHelper.updateCharges(savingsChargeId, savingsId);
        Assert.assertTrue(chargeChanges.containsKey("amount"));

        Integer deletedChargeId = this.savingsAccountHelper.deleteCharge(savingsChargeId, savingsId);
        assertEquals(savingsChargeId, deletedChargeId);

        chargesPendingState = this.savingsAccountHelper.getSavingsCharges(savingsId);
        Assert.assertTrue(chargesPendingState == null || chargesPendingState.size() == 0);

        savingsStatusHashMap = this.savingsAccountHelper.approveSavings(savingsId);
        SavingsStatusChecker.verifySavingsIsApproved(savingsStatusHashMap);

        savingsStatusHashMap = this.savingsAccountHelper.activateSavings(savingsId);
        SavingsStatusChecker.verifySavingsIsActive(savingsStatusHashMap);

        final Integer chargeId = ChargesHelper.createCharges(this.requestSpec, this.responseSpec, ChargesHelper.getSavingsAnnualFeeJSON());
        Assert.assertNotNull(chargeId);

        ArrayList<HashMap> charges = this.savingsAccountHelper.getSavingsCharges(savingsId);
        Assert.assertTrue(charges == null || charges.size() == 0);

        this.savingsAccountHelper.addChargesForSavings(savingsId, chargeId);
        charges = this.savingsAccountHelper.getSavingsCharges(savingsId);
        Assert.assertEquals(1, charges.size());

        HashMap savingsChargeForPay = charges.get(0);
        SimpleDateFormat sdf = new SimpleDateFormat(CommonConstants.dateFormat, Locale.US);
        Calendar cal = Calendar.getInstance();
        List dates = (List) savingsChargeForPay.get("dueDate");
        cal.set(Calendar.YEAR, (Integer) dates.get(0));
        cal.set(Calendar.MONTH, (Integer) dates.get(1) - 1);
        cal.set(Calendar.DAY_OF_MONTH, (Integer) dates.get(2));

        this.savingsAccountHelper.payCharge((Integer) savingsChargeForPay.get("id"), savingsId,
                ((Float) savingsChargeForPay.get("amount")).toString(), sdf.format(cal.getTime()));
        HashMap paidCharge = this.savingsAccountHelper.getSavingsCharge(savingsId, (Integer) savingsChargeForPay.get("id"));
        assertEquals(savingsChargeForPay.get("amount"), paidCharge.get("amountPaid"));

        final Integer monthlyFeechargeId = ChargesHelper.createCharges(this.requestSpec, this.responseSpec,
                ChargesHelper.getSavingsMonthlyFeeJSON());
        Assert.assertNotNull(monthlyFeechargeId);

        this.savingsAccountHelper.addChargesForSavings(savingsId, monthlyFeechargeId);
        charges = this.savingsAccountHelper.getSavingsCharges(savingsId);
        Assert.assertEquals(2, charges.size());

        HashMap savingsChargeForWaive = charges.get(1);
        this.savingsAccountHelper.waiveCharge((Integer) savingsChargeForWaive.get("id"), savingsId);
        HashMap waiveCharge = this.savingsAccountHelper.getSavingsCharge(savingsId, (Integer) savingsChargeForWaive.get("id"));
        assertEquals(savingsChargeForWaive.get("amount"), waiveCharge.get("amountWaived"));

        this.savingsAccountHelper.waiveCharge((Integer) savingsChargeForWaive.get("id"), savingsId);
        waiveCharge = this.savingsAccountHelper.getSavingsCharge(savingsId, (Integer) savingsChargeForWaive.get("id"));
        BigDecimal totalWaiveAmount = BigDecimal.valueOf(Double.valueOf((Float) savingsChargeForWaive.get("amount")));
        totalWaiveAmount = totalWaiveAmount.add(totalWaiveAmount);
        assertEquals(totalWaiveAmount.floatValue(), waiveCharge.get("amountWaived"));

        final Integer weeklyFeeId = ChargesHelper.createCharges(this.requestSpec, this.responseSpec,
                ChargesHelper.getSavingsWeeklyFeeJSON());
        Assert.assertNotNull(weeklyFeeId);

        this.savingsAccountHelper.addChargesForSavings(savingsId, weeklyFeeId);
        charges = this.savingsAccountHelper.getSavingsCharges(savingsId);
        Assert.assertEquals(3, charges.size());

        savingsChargeForPay = charges.get(2);
        cal = Calendar.getInstance();
        dates = (List) savingsChargeForPay.get("dueDate");
        cal.set(Calendar.YEAR, (Integer) dates.get(0));
        cal.set(Calendar.MONTH, (Integer) dates.get(1) - 1);
        cal.set(Calendar.DAY_OF_MONTH, (Integer) dates.get(2));

        // Depositing huge amount as scheduler job deducts the fee amount
        Integer depositTransactionId = (Integer) this.savingsAccountHelper.depositToSavingsAccount(savingsId, "100000",
                SavingsAccountHelper.TRANSACTION_DATE, CommonConstants.RESPONSE_RESOURCE_ID);
        Assert.assertNotNull(depositTransactionId);

        this.savingsAccountHelper.payCharge((Integer) savingsChargeForPay.get("id"), savingsId,
                ((Float) savingsChargeForPay.get("amount")).toString(), sdf.format(cal.getTime()));
        paidCharge = this.savingsAccountHelper.getSavingsCharge(savingsId, (Integer) savingsChargeForPay.get("id"));
        assertEquals(savingsChargeForPay.get("amount"), paidCharge.get("amountPaid"));
        List nextDueDates = (List) paidCharge.get("dueDate");
        LocalDate nextDueDate = new LocalDate((Integer) nextDueDates.get(0), (Integer) nextDueDates.get(1), (Integer) nextDueDates.get(2));
        LocalDate expectedNextDueDate = new LocalDate((Integer) dates.get(0), (Integer) dates.get(1), (Integer) dates.get(2))
                .plusWeeks((Integer) paidCharge.get("feeInterval"));
        assertEquals(expectedNextDueDate, nextDueDate);
    }

    @Test
    public void testSavingsAccountWithSyncInterestWithMeeting_AS_FALSE() {
        this.savingsAccountHelper = new SavingsAccountHelper(this.requestSpec, this.responseSpec);

        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        final Integer groupId = GroupHelper.createGroup(this.requestSpec, this.responseSpec, true);
        Assert.assertNotNull(groupId);

        GroupHelper.associateClient(this.requestSpec, this.responseSpec, groupId.toString(), clientID.toString());
        Assert.assertNotNull(groupId);

        GroupHelper.attachMeeting(requestSpec, responseSpec, groupId.toString(), GroupHelper.CALENDAR_FREQUENCY_MONTHLY, "1",
                CALENDAR_START_DATE);

        Assert.assertNotNull(clientID);
        final String minBalanceForInterestCalculation = "5000";
        final String minRequiredBalance = null;
        final String enforceMinRequiredBalance = "false";
        final boolean allowOverdraft = false;
        final String syncInterestPostingWithMeeting = "false";
        final Integer savingsProductID = createSavingsProduct(this.requestSpec, this.responseSpec, MINIMUM_OPENING_BALANCE,
                minBalanceForInterestCalculation, minRequiredBalance, enforceMinRequiredBalance, syncInterestPostingWithMeeting);
        Assert.assertNotNull(savingsProductID);

        DateFormat dateFormat = new SimpleDateFormat("dd MMMM yyyy");

        Calendar todaysDate = Calendar.getInstance();
        todaysDate.add(Calendar.MONTH, -3);
        System.out.println(dateFormat.format(todaysDate.getTime()));
        todaysDate.set(Calendar.DAY_OF_MONTH, 1);
        final String SAVINGS_ACTIVATION_DATE = dateFormat.format(todaysDate.getTime());

        final Integer savingsId = this.savingsAccountHelper.applyForSavingsApplicationOnDate(clientID, groupId, savingsProductID,
                ACCOUNT_TYPE_JLG, SAVINGS_ACTIVATION_DATE);
        Assert.assertNotNull(savingsProductID);

        HashMap savingsStatusHashMap = SavingsStatusChecker.getStatusOfSavings(this.requestSpec, this.responseSpec, savingsId);
        SavingsStatusChecker.verifySavingsIsPending(savingsStatusHashMap);

        savingsStatusHashMap = this.savingsAccountHelper.approveSavingsOnDate(savingsId, SAVINGS_ACTIVATION_DATE);
        SavingsStatusChecker.verifySavingsIsApproved(savingsStatusHashMap);

        savingsStatusHashMap = this.savingsAccountHelper.activateSavingsAccount(savingsId, SAVINGS_ACTIVATION_DATE);
        SavingsStatusChecker.verifySavingsIsActive(savingsStatusHashMap);

        this.savingsAccountHelper.postInterestForSavings(savingsId);

        HashMap savingsDetails = this.savingsAccountHelper.getSavingsDetails(savingsId);

        @SuppressWarnings("unchecked")
        List<HashMap> transactions = (List<HashMap>) savingsDetails.get("transactions");
        for (HashMap transaction : transactions) {
            HashMap transactionType = (HashMap) transaction.get("transactionType");
            if ((Boolean) transactionType.get("interestPosting")) {
                List transactionDate = (List) transaction.get("date");
                assertEquals(1, transactionDate.get(2));
            }
        }

    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSavingsAccountWithSyncInterestWithMeeting_AS_TRUE() {
        this.savingsAccountHelper = new SavingsAccountHelper(this.requestSpec, this.responseSpec);
        final ResponseSpecification errorResponse = new ResponseSpecBuilder().expectStatusCode(400).build();
        final SavingsAccountHelper validationErrorHelper = new SavingsAccountHelper(this.requestSpec, errorResponse);

        final String minBalanceForInterestCalculation = "5000";
        final String minRequiredBalance = null;
        final String enforceMinRequiredBalance = "false";
        final boolean allowOverdraft = false;
        final String syncInterestPostingWithMeeting = "true";
        final Integer savingsProductID = createSavingsProduct(this.requestSpec, this.responseSpec, MINIMUM_OPENING_BALANCE,
                minBalanceForInterestCalculation, minRequiredBalance, enforceMinRequiredBalance, syncInterestPostingWithMeeting);
        Assert.assertNotNull(savingsProductID);

        DateFormat dateFormat = new SimpleDateFormat("dd MMMM yyyy");

        Calendar todaysDate = Calendar.getInstance();
        todaysDate.add(Calendar.MONTH, -3);
        System.out.println(dateFormat.format(todaysDate.getTime()));
        todaysDate.set(Calendar.DAY_OF_MONTH, 1);
        final String SAVINGS_ACTIVATION_DATE = dateFormat.format(todaysDate.getTime());

        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        Assert.assertNotNull(clientID);
        ArrayList<HashMap> savingsAccountErrorData = (ArrayList<HashMap>) validationErrorHelper.applyForSavingsApplicationOnDate(clientID,
                null, savingsProductID, ACCOUNT_TYPE_INDIVIDUAL, SAVINGS_ACTIVATION_DATE, CommonConstants.RESPONSE_ERROR);
        assertEquals("validation.msg.savingsaccount.syncInterestPostingWithMeeting.group.not.found",
                savingsAccountErrorData.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));

        final Integer groupId = GroupHelper.createGroup(this.requestSpec, this.responseSpec, true);
        Assert.assertNotNull(groupId);

        GroupHelper.associateClient(this.requestSpec, this.responseSpec, groupId.toString(), clientID.toString());
        Assert.assertNotNull(groupId);

        savingsAccountErrorData = (ArrayList<HashMap>) validationErrorHelper.applyForSavingsApplicationOnDate(clientID, groupId,
                savingsProductID, ACCOUNT_TYPE_JLG, SAVINGS_ACTIVATION_DATE, CommonConstants.RESPONSE_ERROR);
        assertEquals("validation.msg.savingsaccount.syncInterestPostingWithMeeting.calendar.not.found",
                savingsAccountErrorData.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));

        GroupHelper.attachMeeting(requestSpec, responseSpec, groupId.toString(), GroupHelper.CALENDAR_FREQUENCY_MONTHLY, "1",
                CALENDAR_START_DATE);

        final Integer savingsId = this.savingsAccountHelper.applyForSavingsApplicationOnDate(clientID, groupId, savingsProductID,
                ACCOUNT_TYPE_JLG, SAVINGS_ACTIVATION_DATE);
        Assert.assertNotNull(savingsProductID);

        HashMap savingsStatusHashMap = SavingsStatusChecker.getStatusOfSavings(this.requestSpec, this.responseSpec, savingsId);
        SavingsStatusChecker.verifySavingsIsPending(savingsStatusHashMap);

        savingsStatusHashMap = this.savingsAccountHelper.approveSavingsOnDate(savingsId, SAVINGS_ACTIVATION_DATE);
        SavingsStatusChecker.verifySavingsIsApproved(savingsStatusHashMap);

        savingsStatusHashMap = this.savingsAccountHelper.activateSavingsAccount(savingsId, SAVINGS_ACTIVATION_DATE);
        SavingsStatusChecker.verifySavingsIsActive(savingsStatusHashMap);

        this.savingsAccountHelper.postInterestForSavings(savingsId);

        HashMap savingsDetails = this.savingsAccountHelper.getSavingsDetails(savingsId);

        List<HashMap> transactions = (List<HashMap>) savingsDetails.get("transactions");
        for (HashMap transaction : transactions) {
            HashMap transactionType = (HashMap) transaction.get("transactionType");
            if ((Boolean) transactionType.get("interestPosting")) {
                List transactionDate = (List) transaction.get("date");
                assertEquals(6, transactionDate.get(2));
            }
        }

    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSavingsAccountWithSyncInterestWithMeeting_AS_TRUE_NOT_SYNC_WITH_POSTING_TYPE() {
        this.savingsAccountHelper = new SavingsAccountHelper(this.requestSpec, this.responseSpec);
        final ResponseSpecification errorResponse = new ResponseSpecBuilder().expectStatusCode(400).build();
        final SavingsAccountHelper validationErrorHelper = new SavingsAccountHelper(this.requestSpec, errorResponse);

        final String minBalanceForInterestCalculation = "5000";
        final String minRequiredBalance = null;
        final String enforceMinRequiredBalance = "false";
        final boolean allowOverdraft = false;
        final String syncInterestPostingWithMeeting = "true";
        final Integer savingsProductID = createSavingsProduct(this.requestSpec, this.responseSpec, MINIMUM_OPENING_BALANCE,
                minBalanceForInterestCalculation, minRequiredBalance, enforceMinRequiredBalance, syncInterestPostingWithMeeting);
        Assert.assertNotNull(savingsProductID);

        DateFormat dateFormat = new SimpleDateFormat("dd MMMM yyyy");

        Calendar todaysDate = Calendar.getInstance();
        todaysDate.add(Calendar.MONTH, -3);
        System.out.println(dateFormat.format(todaysDate.getTime()));
        todaysDate.set(Calendar.DAY_OF_MONTH, 1);
        final String SAVINGS_ACTIVATION_DATE = dateFormat.format(todaysDate.getTime());

        final Integer clientID = ClientHelper.createClient(this.requestSpec, this.responseSpec);
        Assert.assertNotNull(clientID);

        final Integer groupId = GroupHelper.createGroup(this.requestSpec, this.responseSpec, true);
        Assert.assertNotNull(groupId);

        GroupHelper.associateClient(this.requestSpec, this.responseSpec, groupId.toString(), clientID.toString());
        Assert.assertNotNull(groupId);

        GroupHelper.attachMeeting(requestSpec, responseSpec, groupId.toString(), GroupHelper.CALENDAR_FREQUENCY_MONTHLY, "2",
                CALENDAR_START_DATE);

        ArrayList<HashMap> savingsAccountErrorData = (ArrayList<HashMap>) validationErrorHelper.applyForSavingsApplicationOnDate(clientID,
                groupId, savingsProductID, ACCOUNT_TYPE_JLG, SAVINGS_ACTIVATION_DATE, CommonConstants.RESPONSE_ERROR);
        assertEquals("validation.msg.savingsaccount.syncInterestPostingWithMeeting.interest.posting.not.same.as.meeting.frequency",
                savingsAccountErrorData.get(0).get(CommonConstants.RESPONSE_ERROR_MESSAGE_CODE));

    }

    public static Integer createSavingsProduct(final RequestSpecification requestSpec, final ResponseSpecification responseSpec,
            final String minOpenningBalance, final String minBalanceForInterestCalculation, final String minRequiredBalance,
            final String enforceMinRequiredBalance) {
        final String syncInterestPostingWithMeeting = "false";
        return createSavingsProduct(requestSpec, responseSpec, minOpenningBalance, minBalanceForInterestCalculation, minRequiredBalance,
                enforceMinRequiredBalance, syncInterestPostingWithMeeting);
    }

    public static Integer createSavingsProduct(final RequestSpecification requestSpec, final ResponseSpecification responseSpec,
            final String minOpenningBalance, final String minBalanceForInterestCalculation, final String minRequiredBalance,
            final String enforceMinRequiredBalance, final String syncInterestPostingWithMeeting) {
        System.out.println("------------------------------CREATING NEW SAVINGS PRODUCT ---------------------------------------");
        SavingsProductHelper savingsProductHelper = new SavingsProductHelper();
        final String savingsProductJSON = savingsProductHelper //
                .withInterestCompoundingPeriodTypeAsDaily() //
                .withInterestPostingPeriodTypeAsMonthly() //
                .withInterestCalculationPeriodTypeAsDailyBalance() //
                .withMinBalanceForInterestCalculation(minBalanceForInterestCalculation) //
                .withMinRequiredBalance(minRequiredBalance) //
                .withEnforceMinRequiredBalance(enforceMinRequiredBalance) //
                .withSyncInterestPostingWithMeeting(syncInterestPostingWithMeeting)//
                .withMinimumOpenningBalance(minOpenningBalance).build();
        return SavingsProductHelper.createSavingsProduct(savingsProductJSON, requestSpec, responseSpec);
    }
}