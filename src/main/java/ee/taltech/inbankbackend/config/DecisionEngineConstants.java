package ee.taltech.inbankbackend.config;

/**
 * Holds all necessary constants for the decision engine.
 */
public class DecisionEngineConstants {
    public static final Integer MINIMUM_LOAN_AMOUNT = 2000;
    public static final Integer MAXIMUM_LOAN_AMOUNT = 10000;
    public static final Integer MAXIMUM_LOAN_PERIOD = 48;
    public static final Integer MINIMUM_LOAN_PERIOD = 12;
    public static final Integer SEGMENT_1_CREDIT_MODIFIER = 100;
    public static final Integer SEGMENT_2_CREDIT_MODIFIER = 300;
    public static final Integer SEGMENT_3_CREDIT_MODIFIER = 1000;
    
    // Age-related constants
    public static final Integer MINIMUM_AGE = 18;
    
    // Maximum age is determined by country-specific expected lifetime minus maximum loan period
    // These values represent expected lifetime in each country
    public static final Integer ESTONIA_EXPECTED_LIFETIME = 78;
    public static final Integer LATVIA_EXPECTED_LIFETIME = 75;
    public static final Integer LITHUANIA_EXPECTED_LIFETIME = 76;
}
