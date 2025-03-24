# TICKET-101 Review

## Core Issue and Solution

The implementation of TICKET-101 had several key issues that were fixed:

### 1. Incorrect Credit Scoring Algorithm (`DecisionEngine.java`)

**Problem:** The original implementation used an incorrect formula for calculating credit scores:
```java
private int highestValidLoanAmount(int loanPeriod) {
    return creditModifier * loanPeriod;
}
```

**Solution:** Implemented the correct algorithm as specified in requirements:
```java
private double calculateCreditScore(Long loanAmount, int loanPeriod) {
    return ((double) creditModifier / loanAmount) * loanPeriod / 10;
}
```
This fix ensures proper risk assessment with loan decisions based on the required 0.1 threshold.

### 2. Inefficient Maximum Loan Amount Calculation (`DecisionEngine.java`)

**Problem:** There was no efficient way to determine the maximum approved loan amount for a given period.

**Solution:** Implemented a binary search algorithm in the `findMaximumApprovedAmount()` method to efficiently find the highest loan amount that meets the credit score threshold:
```java
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
```
This binary search approach is much more efficient than trying each possible loan amount, providing better performance especially for larger ranges.

### 3. Missing Period Adjustment Feature (`DecisionEngine.java`)

**Problem:** The engine didn't properly attempt to find alternative loan periods when no suitable amount was found.

**Solution:** Implemented the `findAlternativeLoan()` method that tries different periods to find an approved loan:
```java
private Decision findAlternativeLoan() {
    ...
}
```

### 4. Incorrect Period Constraints (`DecisionEngineConstants.java`)

**Problem:** Maximum loan period was set to 60 months instead of the required 48 months.

**Solution:** Updated the constant to match requirements (updated in `loan_form.dart` and `DecisionEngineConstraints.java` files):
```java
public static final Integer MAXIMUM_LOAN_PERIOD = 48;
```

### 5. Missing Default Constructor (`DecisionRequest.java`)

**Problem:** `DecisionRequest` lacked a no-args constructor required by Jackson for deserializing JSON.

**Solution:** Added `@NoArgsConstructor` annotation:
```java
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class DecisionRequest {
    // Fields...
}
```

### 6. Frontend Not Showing Maximum Approved Amount (`loan_form.dart`)

**Problem:** The frontend conditionally displayed user's selected values instead of always showing the maximum approved amount:
```dart
if (tempAmount <= _loanAmount || tempPeriod > _loanPeriod) {
  _loanAmountResult = int.parse(result['loanAmount'].toString());
  _loanPeriodResult = int.parse(result['loanPeriod'].toString());
} else {
  _loanAmountResult = _loanAmount;
  _loanPeriodResult = _loanPeriod;
}
```

**Solution:** Modified logic to always display server's response:
```dart
// Always use the server's response
_loanAmountResult = int.parse(result['loanAmount'].toString());
_loanPeriodResult = int.parse(result['loanPeriod'].toString());
```

These fixes ensure the application correctly implements all requirements and provides accurate loan decisions. 