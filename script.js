const buttons = document.querySelectorAll(".channel-card button");

buttons.forEach((button) => {
    button.addEventListener("click", () => {
        const card = button.closest(".channel-card");
        const channelName = card.querySelector("h3").innerText;

     window.location.href = "player.html";   
    });
});
