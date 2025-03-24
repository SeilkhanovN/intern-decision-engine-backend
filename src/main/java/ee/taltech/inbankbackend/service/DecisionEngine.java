package ee.taltech.inbankbackend.service;

import com.github.vladislavgoltjajev.personalcode.locale.estonia.EstonianPersonalCodeValidator;
import ee.taltech.inbankbackend.config.DecisionEngineConstants;
import ee.taltech.inbankbackend.exceptions.InvalidAgeException;
import ee.taltech.inbankbackend.exceptions.InvalidLoanAmountException;
import ee.taltech.inbankbackend.exceptions.InvalidLoanPeriodException;
import ee.taltech.inbankbackend.exceptions.InvalidPersonalCodeException;
import ee.taltech.inbankbackend.exceptions.NoValidLoanException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;

/**
 * A service class that provides a method for calculating an approved loan amount and period for a customer.
 * The loan amount is calculated based on the customer's credit modifier,
 * which is determined by the last four digits of their ID code.
 */
@Service
public class DecisionEngine {

    // Used to check for the validity of the presented ID code.
    private final EstonianPersonalCodeValidator validator = new EstonianPersonalCodeValidator();
    private int creditModifier = 0;

    /**
     * Calculates the maximum loan amount and period for the customer based on their ID code,
     * the requested loan amount and the loan period.
     * The loan period must be between 12 and 48 months (inclusive).
     * The loan amount must be between 2000 and 10000€ months (inclusive).
     *
     * @param personalCode ID code of the customer that made the request.
     * @param loanAmount Requested loan amount
     * @param loanPeriod Requested loan period
     * @return A Decision object containing the approved loan amount and period, and an error message (if any)
     * @throws InvalidPersonalCodeException If the provided personal ID code is invalid
     * @throws InvalidLoanAmountException If the requested loan amount is invalid
     * @throws InvalidLoanPeriodException If the requested loan period is invalid
     * @throws NoValidLoanException If there is no valid loan found for the given ID code, loan amount and loan period
     * @throws InvalidAgeException If the customer's age does not meet requirements
     */
    public Decision calculateApprovedLoan(String personalCode, Long loanAmount, int loanPeriod)
            throws InvalidPersonalCodeException, InvalidLoanAmountException, InvalidLoanPeriodException,
            NoValidLoanException, InvalidAgeException {
        // Validate inputs - always throw exceptions for consistency instead of returning a Decision
        verifyInputs(personalCode, loanAmount, loanPeriod);
        
        // Verify age restrictions
        verifyAgeRestrictions(personalCode, loanPeriod);

        creditModifier = getCreditModifier(personalCode);

        if (creditModifier == 0) {
            throw new NoValidLoanException("No valid loan found!");
        }

        try {
            // First try with the original requested loan amount and period
            double creditScore = calculateCreditScore(loanAmount, loanPeriod);
            
            if (creditScore >= 0.1) {
                // If the requested amount is approved, check if we can approve a higher amount
                Integer maximumApprovedAmount = findMaximumApprovedAmount(loanPeriod);
                
                if (maximumApprovedAmount != null) {
                    Decision decision = new Decision(maximumApprovedAmount, loanPeriod, null);
                    return decision;
                }
            }
            
            // If we get here, either the requested amount is not approved or we couldn't find a maximum
            // Try to find a lower amount that could be approved
            Integer maximumApprovedAmount = findMaximumApprovedAmount(loanPeriod);
            
            if (maximumApprovedAmount != null && maximumApprovedAmount >= DecisionEngineConstants.MINIMUM_LOAN_AMOUNT) {
                // Found an approved amount within the same period
                Decision decision = new Decision(maximumApprovedAmount, loanPeriod, null);
                return decision;
            } else {
                // Try to find an amount with a different period
                Decision alternativeLoan = findAlternativeLoan(loanAmount);
                
                if (alternativeLoan != null) {
                    return alternativeLoan;
                }
                
                throw new NoValidLoanException("No valid loan found!");
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new NoValidLoanException("No valid loan found: " + e.getMessage());
        }
    }
    
    /**
     * Verifies that customer's age meets the requirements for loan approval.
     * Customers must be at least 18 years old and not exceed the maximum age for their country.
     * 
     * @param personalCode Customer's personal code
     * @param loanPeriod Requested loan period in months
     * @throws InvalidAgeException If customer's age doesn't meet requirements
     * @throws InvalidPersonalCodeException If personal code format is invalid
     */
    private void verifyAgeRestrictions(String personalCode, int loanPeriod) throws InvalidAgeException, InvalidPersonalCodeException {
        if (!validator.isValid(personalCode)) {
            throw new InvalidPersonalCodeException("Invalid personal ID code!");
        }
        
        // Calculate customer's age
        int age = calculateAge(personalCode);
        
        // Check if customer is at least 18 years old
        if (age < DecisionEngineConstants.MINIMUM_AGE) {
            throw new InvalidAgeException("Customer is underage. Minimum age for loan approval is " 
                    + DecisionEngineConstants.MINIMUM_AGE + " years.");
        }
        
        // Determine customer's country and corresponding expected lifetime
        int expectedLifetime = determineExpectedLifetime(personalCode);
        
        // Maximum age = expected lifetime - loan period in years
        int loanPeriodInYears = (int) Math.ceil(loanPeriod / 12.0);
        int maximumAge = expectedLifetime - loanPeriodInYears;
        
        // Check if customer's age doesn't exceed maximum
        if (age > maximumAge) {
            throw new InvalidAgeException("Customer exceeds maximum age for loan approval in their country.");
        }
    }
    
    /**
     * Calculates customer's age based on their personal code.
     * Estonian personal code format: GYYMMDDSSSC, where:
     * - G is gender/century (3-4 for 1900s, 5-6 for 2000s, 1-2 for 1800s)
     * - YYMMDD is date of birth
     * - SSS is a serial number
     * - C is a checksum
     * 
     * @param personalCode Customer's personal code
     * @return Customer's age in years
     */
    private int calculateAge(String personalCode) {
        // Get century indicator digit
        int centuryDigit = Character.getNumericValue(personalCode.charAt(0));
        
        // Determine birth year
        int year;
        if (centuryDigit == 3 || centuryDigit == 4) {
            // 1900s
            year = 1900 + Integer.parseInt(personalCode.substring(1, 3));
        } else if (centuryDigit == 5 || centuryDigit == 6) {
            // 2000s
            year = 2000 + Integer.parseInt(personalCode.substring(1, 3));
        } else {
            // 1800s
            year = 1800 + Integer.parseInt(personalCode.substring(1, 3));
        }
        
        // Determine birth month and day
        int month = Integer.parseInt(personalCode.substring(3, 5));
        int day = Integer.parseInt(personalCode.substring(5, 7));
        
        // Calculate age
        LocalDate birthDate = LocalDate.of(year, month, day);
        LocalDate currentDate = LocalDate.now();
        
        return Period.between(birthDate, currentDate).getYears();
    }
    
    /**
     * Determines expected lifetime based on customer's country.
     * 
     * The method uses the 8th digit of the personal code modulo 3:
     * - Remainder 0: Estonian
     * - Remainder 1: Latvian  
     * - Remainder 2: Lithuanian
     * 
     * @param personalCode Customer's personal code
     * @return Expected lifetime in years for customer's country
     */
    private int determineExpectedLifetime(String personalCode) {
        int eighthDigit = Character.getNumericValue(personalCode.charAt(7));
        int remainder = eighthDigit % 3;
        
        if (remainder == 0) {
            // Estonian personal code
            return DecisionEngineConstants.ESTONIA_EXPECTED_LIFETIME;
        } else if (remainder == 1) {
            // Latvian personal code
            return DecisionEngineConstants.LATVIA_EXPECTED_LIFETIME;
        } else {
            // Lithuanian personal code
            return DecisionEngineConstants.LITHUANIA_EXPECTED_LIFETIME;
        }
    }

    /**
     * Calculates the credit score based on the specified algorithm:
     * credit score = ((credit modifier / loan amount) * loan period) / 10
     *
     * @param loanAmount Requested loan amount
     * @param loanPeriod Requested loan period
     * @return The calculated credit score
     */
    private double calculateCreditScore(Long loanAmount, int loanPeriod) {
        if (loanAmount == null || loanAmount == 0) {
            return 0;
        }
        double result = ((double) creditModifier / loanAmount) * loanPeriod / 10;
        return result;
    }

    /**
     * Finds the maximum loan amount that can be approved for the given period.
     *
     * @param loanPeriod The loan period in months
     * @return The maximum approved loan amount, or null if no valid amount found
     */
    private Integer findMaximumApprovedAmount(int loanPeriod) {
        // Binary search to find the maximum approved amount
        int left = DecisionEngineConstants.MINIMUM_LOAN_AMOUNT;
        int right = DecisionEngineConstants.MAXIMUM_LOAN_AMOUNT;
        Integer result = null;

        while (left <= right) {
            int mid = left + (right - left) / 2;
            double score = calculateCreditScore((long) mid, loanPeriod);
            
            if (score >= 0.1) {
                result = mid;
                // Try to find a larger amount
                left = mid + 1;
            } else {
                // Amount too high, try lower
                right = mid - 1;
            }
        }
        
        return result;
    }

    /**
     * Finds an alternative loan by trying different periods when the original period
     * doesn't yield an approved amount.
     *
     * @return A Decision with an approved loan amount and period, or null if none found
     */
    private Decision findAlternativeLoan(Long requestedAmount) {
        // First try to find alternative period for requested amount
        // Binary search to find minimum valid period for requested amount
        int left = DecisionEngineConstants.MINIMUM_LOAN_PERIOD;
        int right = DecisionEngineConstants.MAXIMUM_LOAN_PERIOD;
        Integer validPeriod = null;

        while (left <= right) {
            int mid = left + (right - left) / 2;
            double score = calculateCreditScore(requestedAmount, mid);

            if (score >= 0.1) {
                validPeriod = mid;
                // Try to find a smaller period
                right = mid - 1;
            } else {
                left = mid + 1;
            }
        }

        if (validPeriod != null) {
            Decision decision = new Decision(requestedAmount.intValue(), validPeriod, null);
            return decision;
        }
        
        // If no alternative found for requested amount, try any amount
        for (int period = DecisionEngineConstants.MINIMUM_LOAN_PERIOD; 
             period <= DecisionEngineConstants.MAXIMUM_LOAN_PERIOD; 
             period++) {
            Integer approvedAmount = findMaximumApprovedAmount(period);
            
            if (approvedAmount != null && approvedAmount >= DecisionEngineConstants.MINIMUM_LOAN_AMOUNT) {
                Decision decision = new Decision(approvedAmount, period, null);
                return decision;
            }
        }
        
        return null;
    }

    /**
     * Calculates the credit modifier of the customer to according to the last four digits of their ID code.
     * Debt - 0000...2499
     * Segment 1 - 2500...4999
     * Segment 2 - 5000...7499
     * Segment 3 - 7500...9999
     *
     * @param personalCode ID code of the customer that made the request.
     * @return Segment to which the customer belongs.
     */
    private int getCreditModifier(String personalCode) {
        int segment = Integer.parseInt(personalCode.substring(personalCode.length() - 4));

        if (segment < 2500) {
            return 0;
        } else if (segment < 5000) {
            return DecisionEngineConstants.SEGMENT_1_CREDIT_MODIFIER;
        } else if (segment < 7500) {
            return DecisionEngineConstants.SEGMENT_2_CREDIT_MODIFIER;
        }

        return DecisionEngineConstants.SEGMENT_3_CREDIT_MODIFIER;
    }

    /**
     * Verify that all inputs are valid according to business rules.
     * If inputs are invalid, then throws corresponding exceptions.
     *
     * @param personalCode Provided personal ID code
     * @param loanAmount Requested loan amount
     * @param loanPeriod Requested loan period
     * @throws InvalidPersonalCodeException If the provided personal ID code is invalid
     * @throws InvalidLoanAmountException If the requested loan amount is invalid
     * @throws InvalidLoanPeriodException If the requested loan period is invalid
     */
    private void verifyInputs(String personalCode, Long loanAmount, int loanPeriod)
            throws InvalidPersonalCodeException, InvalidLoanAmountException, InvalidLoanPeriodException {
        if (personalCode == null || personalCode.isEmpty()) {
            throw new InvalidPersonalCodeException("Personal code cannot be empty!");
        }

        if (!validator.isValid(personalCode)) {
            throw new InvalidPersonalCodeException("Invalid personal ID code!");
        }
        
        if (loanAmount == null) {
            throw new InvalidLoanAmountException("Loan amount cannot be null!");
        }
        
        if (!(DecisionEngineConstants.MINIMUM_LOAN_AMOUNT <= loanAmount)
                || !(loanAmount <= DecisionEngineConstants.MAXIMUM_LOAN_AMOUNT)) {
            throw new InvalidLoanAmountException("Invalid loan amount!");
        }
        
        if (!(DecisionEngineConstants.MINIMUM_LOAN_PERIOD <= loanPeriod)
                || !(loanPeriod <= DecisionEngineConstants.MAXIMUM_LOAN_PERIOD)) {
            throw new InvalidLoanPeriodException("Invalid loan period!");
        }
    }
}
