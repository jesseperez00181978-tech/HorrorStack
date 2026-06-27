const saveButton = document.querySelector(".modal-content button");
const movieList = document.querySelector("#movieList");

saveButton.addEventListener("click", () => {
    const title = document.querySelector("#movieTitle").value;
    const year = document.querySelector("#movieYear").value;
    const genre = document.querySelector("#movieGenre").value;
    const rating = document.querySelector("#movieRating").value;

    const movieCard = document.createElement("div");
    movieCard.classList.add("movie-card");

    movieCard.innerHTML = `
        <h3>🎬 ${title} (${year})</h3>
        <p>Genre: ${genre}</p>
        <p>Rating: ⭐ ${rating}/10</p>
    `;

    movieList.appendChild(movieCard);

    document.querySelector("#movieTitle").value = "";
    document.querySelector("#movieYear").value = "";
    document.querySelector("#movieGenre").value = "";
    document.querySelector("#movieRating").value = "";
});
