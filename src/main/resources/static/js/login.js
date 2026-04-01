function login() {
    const username = document.getElementById("username").value;
    const password = document.getElementById("password").value;

    if (!username || !password) {
        alert("Please enter both username and password.");
        return;
    }

    fetch("/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password })
    })
        .then(r => r.text())
        .then(t => {
            if (t === "OK") {
                window.location.href = "/";  // ✅ use .href for clarity
            } else {
                alert(t);
            }
        })
        .catch(() => alert("Network error. Please try again."));
}
