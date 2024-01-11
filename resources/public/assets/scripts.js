// Copyright © 2015-2024 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0


// Source: https://xvello.net/blog/htmx-error-handling/
document.body.addEventListener('htmx:afterRequest', function (evt) {
  const errorTarget = document.getElementById("htmx-alert")
  if (evt.detail.successful) {
    // Successful request, clear out alert
    errorTarget.setAttribute("hidden", "true")
    errorTarget.innerText = "";
  } else if (evt.detail.failed && evt.detail.xhr) {
    // Server error with response contents, equivalent to htmx:responseError
    console.warn("Server error", evt.detail)
    const xhr = evt.detail.xhr;
    errorTarget.innerText = `Unexpected server error: ${xhr.status} - ${xhr.statusText}`;
    errorTarget.removeAttribute("hidden");
  } else {
    // Unspecified failure, usually caused by network error
    console.error("Unexpected htmx error", evt.detail)
    errorTarget.innerText = "Unexpected error, check your connection and try to refresh the page.";
    errorTarget.removeAttribute("hidden");
  }
});

document.body.addEventListener("do-not-calls-was-updated", function (event) {
  console.log("event", event);
})