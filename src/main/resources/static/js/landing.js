document.addEventListener("DOMContentLoaded", () => {


    /* =================================================
       SCROLL REVEAL
    ================================================= */

    const revealElements =
        document.querySelectorAll(".reveal");


    const revealObserver =
        new IntersectionObserver(
            (entries) => {

                entries.forEach((entry) => {

                    if (entry.isIntersecting) {

                        entry.target.classList.add(
                            "visible"
                        );

                        revealObserver.unobserve(
                            entry.target
                        );

                    }

                });

            },
            {
                threshold: 0.12
            }
        );


    revealElements.forEach((element) => {

        revealObserver.observe(element);

    });



    /* =================================================
       NAVBAR SCROLL
    ================================================= */

    const navbar =
        document.querySelector(".navbar");


    window.addEventListener("scroll", () => {

        if (!navbar) return;


        if (window.scrollY > 40) {

            navbar.style.background =
                "rgba(33, 40, 59, 0.98)";

            navbar.style.boxShadow =
                "0 8px 30px rgba(20, 30, 50, 0.12)";

        } else {

            navbar.style.background =
                "rgba(33, 40, 59, 0.94)";

            navbar.style.boxShadow =
                "none";

        }

    });



    /* =================================================
       3D MOUSE PARALLAX
    ================================================= */

    const scene =
        document.querySelector(".three-d-scene");

    const object =
        document.querySelector(".finance-object");


    if (scene && object) {

        let targetX = 0;
        let targetY = 0;

        let currentX = 0;
        let currentY = 0;


        scene.addEventListener(
            "mousemove",
            (event) => {

                const rect =
                    scene.getBoundingClientRect();


                const x =
                    (event.clientX - rect.left)
                    / rect.width;


                const y =
                    (event.clientY - rect.top)
                    / rect.height;


                targetY =
                    (x - 0.5) * 14;


                targetX =
                    (y - 0.5) * -10;

            }
        );


        scene.addEventListener(
            "mouseleave",
            () => {

                targetX = 0;
                targetY = 0;

            }
        );


        function animate3D() {

            currentX +=
                (targetX - currentX) * 0.08;


            currentY +=
                (targetY - currentY) * 0.08;


            object.style.transform = `
                rotateX(${8 + currentX}deg)
                rotateY(${-13 + currentY}deg)
                rotateZ(2deg)
            `;


            requestAnimationFrame(
                animate3D
            );

        }


        animate3D();

    }



    /* =================================================
       DYNAMIC INCOME / EXPENSE VALUES
    ================================================= */

    const income =
        document.querySelector(".income-value");


    const expense =
        document.querySelector(".expense-value");


    const floatingIncome =
        document.querySelector(
            ".transaction-two .income-text"
        );


    const floatingExpense =
        document.querySelector(
            ".transaction-one > span"
        );


    if (
        income &&
        expense &&
        floatingIncome &&
        floatingExpense
    ) {

        const data = [

            {
                income: "₹68,500",
                expense: "₹31,240"
            },

            {
                income: "₹70,200",
                expense: "₹30,850"
            },

            {
                income: "₹69,750",
                expense: "₹32,410"
            },

            {
                income: "₹72,100",
                expense: "₹29,870"
            },

            {
                income: "₹68,500",
                expense: "₹31,240"
            }

        ];


        let index = 0;


        setInterval(() => {

            index =
                (index + 1)
                % data.length;


            [
                income,
                expense,
                floatingIncome,
                floatingExpense
            ].forEach((element) => {

                element.style.opacity = "0";

            });


            setTimeout(() => {

                income.textContent =
                    data[index].income;


                expense.textContent =
                    data[index].expense;


                floatingIncome.textContent =
                    "+" + data[index].income;


                floatingExpense.textContent =
                    "−₹420";


                [
                    income,
                    expense,
                    floatingIncome,
                    floatingExpense
                ].forEach((element) => {

                    element.style.opacity = "1";

                });

            }, 180);

        }, 4000);

    }



    /* =================================================
       SMOOTH ANCHOR SCROLL
    ================================================= */

    document
        .querySelectorAll('a[href^="#"]')
        .forEach((link) => {

            link.addEventListener(
                "click",
                (event) => {

                    const targetId =
                        link.getAttribute("href");


                    if (
                        !targetId ||
                        targetId === "#"
                    ) {
                        return;
                    }


                    const target =
                        document.querySelector(
                            targetId
                        );


                    if (!target) return;


                    event.preventDefault();


                    target.scrollIntoView({
                        behavior: "smooth",
                        block: "start"
                    });

                }
            );

        });


});