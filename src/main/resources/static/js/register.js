function register() {

    const username =
        document.getElementById("username").value;

    const email =
        document.getElementById("email").value;

    const password =
        document.getElementById("password").value;
    function validateEmail(email) {
        return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
    }
    if (!validateEmail(email)) {
        document.getElementById("errorMsg").textContent = "Please enter a valid email address.";
        return;
    }




    fetch("/auth/register", {

        method: "POST",

        headers: {
            "Content-Type": "application/json"
        },

        body: JSON.stringify({
            username: username,
            email: email,
            password: password
        })

    })
        .then(res => res.text())
        .then(t => {

            if (t === "Registered") {

                alert("Registered successfully");

                window.location.href = "/login-page";

            } else {

                alert(t);

            }

        });

}