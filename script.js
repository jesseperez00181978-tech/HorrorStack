const saveButton = document.querySelector(".modal-content button");
const movieList = document.querySelector("#movieList");

let movies = JSON.parse(localStorage.getItem("horrorMovies")) || [];
movies = movies.filter(movie => movie.title && movie.year && movie.genre && movie.rating);
localStorage.setItem("horrorMovies", JSON.stringify(movies));

function displayMovies() {
    movieList.innerHTML = "";

    movies.forEach((movie, index) => {
        const movieCard = document.createElement("div");
        movieCard.classList.add("movie-card");

        movieCard.innerHTML = `
    <h3>🎬 ${movie.title} (${movie.year})</h3>
    <p>Genre: ${movie.genre}</p>
    <p>Rating: ⭐ ${movie.rating}/10</p>
    <button class="delete-btn">🗑️ Delete</button>
`;

        movieList.appendChild(movieCard);
    });
movieCard.querySelector(".delete-btn").addEventListener("click", () => {
    movies.splice(index, 1);
    localStorage.setItem("horrorMovies", JSON.stringify(movies));
    displayMovies();
});
}

saveButton.addEventListener("click", () => {
    const title = document.querySelector("#movieTitle").value;
    const year = document.querySelector("#movieYear").value;
    const genre = document.querySelector("#movieGenre").value;
    const rating = document.querySelector("#movieRating").value;
if (!title || !year || !genre || !rating) {
    return;
}
    const movie = {
        title: title,
        year: year,
        genre: genre,
        rating: rating
    };

    movies.push(movie);
    localStorage.setItem("horrorMovies", JSON.stringify(movies));

    displayMovies();

    document.querySelector("#movieTitle").value = "";
    document.querySelector("#movieYear").value = "";
    document.querySelector("#movieGenre").value = "";
    document.querySelector("#movieRating").value = "";
});

displayMovies();
