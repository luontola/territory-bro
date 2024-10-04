#!/usr/bin/env bash

# check if asdf was restored from cache
if [ -d ~/.asdf ]; then
  . "$HOME/.asdf/asdf.sh"
  return
fi

git clone https://github.com/asdf-vm/asdf.git ~/.asdf --branch v0.14.1
. "$HOME/.asdf/asdf.sh"
asdf plugin add java
asdf plugin add nodejs
asdf install
