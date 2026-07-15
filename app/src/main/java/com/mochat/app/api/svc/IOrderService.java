package com.mochat.app.api.svc;

import android.os.Parcelable;

import com.mochat.app.api.IMochatService;

/**
 * E-commerce order service (chains #8, #9, #11).
 *
 * <p>Parcels of type {@link PaymentOrder} cross the exported binder into the
 * {@code OrderService}. The {@code writeToParcel}/{@code readFromParcel} pair on the
 * impl disagree on layout, which yields the app-level parcel-mismatch primitive
 * (chain #11).</p>
 */
public interface IOrderService extends IMochatService {

    /** Process an order; returns a confirmation code. */
    String checkout(PaymentOrder order);

    /** Installs an update archive (zip). Vulnerable to ZipSlip (chain #9). */
    boolean installUpdate(byte[] zipBytes);

    /** Loads a plugin module. Vulnerable to dynamic-load RCE (chain #8). */
    boolean loadPlugin(String splitPath);

    /**
     * Parcelable crossing the exported binder. The deliberate mismatch between
     * {@link #writeToParcel} and the constructor's read order is the chain #11
     * primitive.
     */
    class PaymentOrder implements Parcelable {
        public String orderId;
        public long   amountCents;
        public String userId;
        public boolean paid;

        public PaymentOrder() {}

        protected PaymentOrder(android.os.Parcel in) {
            // MISMATCH: read in a different order than writeToParcel writes.
            userId        = in.readString();
            amountCents   = in.readLong();
            paid          = in.readByte() != 0;
            orderId       = in.readString();
        }

        @Override public void writeToParcel(android.os.Parcel dest, int flags) {
            // ORDER: orderId, amountCents, userId, paid
            dest.writeString(orderId);
            dest.writeLong(amountCents);
            dest.writeString(userId);
            dest.writeByte((byte) (paid ? 1 : 0));
        }

        @Override public int describeContents() { return 0; }

        public static final Creator<PaymentOrder> CREATOR = new Creator<>() {
            public PaymentOrder createFromParcel(android.os.Parcel in) { return new PaymentOrder(in); }
            public PaymentOrder[] newArray(int size) { return new PaymentOrder[size]; }
        };
    }
}
