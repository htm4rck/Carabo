package io.carabo.app.service;

import io.carabo.core.dto.EmitDocumentRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/** Producer-owned original cents for a partial PE credit, bounded to a one-cent rounding residual. */
final class OriginalCreditAmounts {
    record Amounts(BigDecimal net, BigDecimal tax) {}
    private static final BigDecimal CENT = new BigDecimal("0.01");
    static Amounts resolve(String type, String reason, String currency, BigDecimal quantity,
            BigDecimal price, BigDecimal discount, String affectation, EmitDocumentRequest.OriginalAmounts original) {
        if (original == null) return null;
        if (!"07".equals(type) || !Set.of("01", "06", "07").contains(reason == null ? "" : reason)
                || !"PEN".equals(currency) || !"10".equals(affectation) || quantity == null || price == null
                || quantity.signum() <= 0 || price.signum() <= 0 || discount != null && discount.signum() != 0
                || original.getNet() == null || original.getTax() == null) {
            throw new IllegalArgumentException("ORIGINAL_CREDIT_CENTS_PROFILE_REQUIRED");
        }
        BigDecimal net, tax;
        try { net=original.getNet().setScale(2, RoundingMode.UNNECESSARY); tax=original.getTax().setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException failure) { throw new IllegalArgumentException("ORIGINAL_CREDIT_CENTS_SCALE_REQUIRED"); }
        BigDecimal raw=quantity.multiply(price), expectedNet=raw.setScale(2,RoundingMode.HALF_UP), expectedTax=raw.multiply(new BigDecimal("0.18")).setScale(2,RoundingMode.HALF_UP);
        if (net.signum()<0 || tax.signum()<0 || net.add(tax).signum()<=0 || net.precision()>14 || tax.precision()>14
                || net.subtract(expectedNet).abs().compareTo(CENT)>0 || tax.subtract(expectedTax).abs().compareTo(CENT)>0) {
            throw new IllegalArgumentException("ORIGINAL_CREDIT_CENT_ALLOCATION_LIMIT");
        }
        return new Amounts(net,tax);
    }
}
