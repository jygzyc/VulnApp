package com.mochat.app.api.svc;

import com.mochat.app.api.IMochatService;

/**
 * Wallet / payment service interface.
 *
 * <p>Chain anchors: {@link #pay(String, long)} is reachable through the exported
 * PaymentActivity and the exported WalletService (Messenger IPC). The interface
 * signature is kept deliberately simple so the analyst must trace through the
 * ServiceLocator&rarr;Proxy&rarr;Handler&rarr;reflection path to find the real
 * {@code PaymentServiceImpl#pay} that skips the PIN check.</p>
 */
public interface IPaymentService extends IMochatService {

    /**
     * @param toAccountId  recipient account id (attacker-controlled in the chain)
     * @param amountCents  amount in fen (cents)
     * @return a transaction id, or {@code null} on auth failure
     */
    String pay(String toAccountId, long amountCents);

    /** @return current balance in fen, decrypted via the native oracle. */
    long balance();

    /** Master-password gate. The vulnerable impl returns {@code true} when the
     *  exported activity calls it without a PIN extra (chain #1). */
    boolean verifyPin(String pin);
}
