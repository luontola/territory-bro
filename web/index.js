require("./style.css");
var Auth0Lock = require("auth0-lock").default;

var lock = new Auth0Lock('8tVkdfnw8ynZ6rXNndD6eZ6ErsHdIgPi', 'luontola.eu.auth0.com');

lock.on("authenticated", function(authResult) {
  console.log("authResult", authResult);
  lock.getProfile(authResult.idToken, function(error, profile) {
    if (error) {
      console.log("auth error", error);
      return;
    }
    console.log("profile", profile);
    localStorage.setItem('id_token', authResult.idToken);
  });
});

var btn_login = document.getElementById('btn-login');
btn_login.addEventListener('click', function() {
  lock.show();
});
