// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

export const siteTitle = "Territory Bro";

const PageTitle = ({title}) => {
  if (title === siteTitle) {
    document.title = title;
  } else {
    document.title = `${title} - ${siteTitle}`;
  }
  return <h1>{title}</h1>
}

export default PageTitle;
