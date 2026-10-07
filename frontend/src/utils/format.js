export function formatKobo(kobo) {

    if (kobo == null) return "₦0.00";
    return new Intl.NumberFormat("en-NG", {
        style: "currency",
        currency: "NGN",
        minimumFractionDigits: 2
    }).format(kobo / 100);
}

export function parseNairaToKobo(input) {

    if (!input) return null;
    const cleaned = input.toString().replace(/[₦,]/g, "").trim();
    const parsed = parseFloat(cleaned);
    if (isNaN(parsed)) return null;
    return Math.round(parsed * 100);
}

export function maskAcct(acct) {

    if (!acct || acct.length < 10) return acct;
    return acct.substring(0, 4) + "****" + acct.substring(acct.length - 2);
}

export function formatDate(isoString) {

    if (!isoString) return "";
    return new Intl.DateTimeFormat("en-NG", {
        day: "2-digit",
        month: "short",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit"
    }).format(new Date(isoString));
}