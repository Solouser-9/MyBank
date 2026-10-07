









export function passwordProblems(pw) {

    const problems = [];
    if (!pw || pw.length < 8) problems.push('at least 8 characters');
    if (pw && pw.length > 128) problems.push('at most 128 characters');
    if (pw && !/[A-Z]/.test(pw)) problems.push('an uppercase letter');
    if (pw && !/[a-z]/.test(pw)) problems.push('a lowercase letter');
    if (pw && !/[0-9]/.test(pw)) problems.push('a number');
    return problems;
}


export function nameProblems(name, label) {

    const problems = [];
    if (!name || !name.trim()) problems.push(`Enter your ${label.toLowerCase()}`);
    else if (name.trim().length > 50) problems.push(`${label} must be 50 characters or fewer`);
    return problems;
}



export function emailProblems(email) {

    const problems = [];
    if (!email || !email.trim()) problems.push('Enter your email');
    else if (!/^[A-Za-z0-9+_.-]+@(.+)$/.test(email.trim())) problems.push('Enter a valid email address');
    return problems;
}

export function phoneProblems(phone) {

    const problems = [];
    if (!phone || !phone.trim()) problems.push('Enter your phone number');
    else if (!/^\+[0-9]{10,15}$/.test(phone.trim()))
        problems.push('Use international format, e.g. +2348012345678');
    return problems;
}


export function pinProblems(pin) {

    const problems = [];
    if (!pin || !pin.trim()) problems.push('Choose a PIN');
    else if (!/^\d{4,6}$/.test(pin.trim())) problems.push('PIN must be 4-6 digits');
    return problems;
}


export function otpProblems(code) {

    const problems = [];
    if (!code || !code.trim()) problems.push('Enter the 6-digit code');
    else if (!/^\d{6}$/.test(code.trim())) problems.push('Code must be exactly 6 digits');
    return problems;
}