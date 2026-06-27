const button = document.querySelector("button");

button.addEventListener("click", () => {
    const title = document.querySelector("#movieTitle").value;
    const year = document.querySelector("#movieYear").value;
    const genre = document.querySelector("#movieGenre").value;
    const rating = document.querySelector("#movieRating").value;

    console.log(title);
    console.log(year);
    console.log(genre);
    console.log(rating);
});
