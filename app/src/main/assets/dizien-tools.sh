#!/bin/bash
# dizien-tools - installed by DiZien Terminal (independent app, not affiliated with Debian).
# A simple numbered menu that installs groups of common tools with apt.
# Nothing is installed unless you choose it and confirm.
export DEBIAN_FRONTEND=noninteractive
export DEBCONF_NONINTERACTIVE_SEEN=true
export LC_ALL=C

G="\e[38;5;108m"; R="\e[38;5;167m"; A="\e[38;5;214m"; D="\e[38;5;245m"; B="\e[1m"; N="\e[0m"

# group number -> label and packages
label_of() { case "$1" in
  1) echo "Basics";; 2) echo "Python";; 3) echo "Build tools";; 4) echo "Node.js";; 5) echo "Network tools";; 6) echo "Security tools";; esac; }
pkgs_of() { case "$1" in
  1) echo "git curl wget vim htop tree zip unzip";;
  2) echo "python3 python3-pip python3-venv";;
  3) echo "build-essential cmake";;
  4) echo "nodejs npm";;
  5) echo "iputils-ping bind9-dnsutils net-tools openssh-client";; 6) echo "nmap sqlmap hydra john wfuzz";; esac; }

is_installed() { # is_installed pkg
  [ "$(dpkg-query -W -f='${db:Status-Status}' "$1" 2>/dev/null)" = "installed" ]
}
all_installed() { local p; for p in $1; do is_installed "$p" || return 1; done; return 0; }
missing_of() { local p out=""; for p in $1; do is_installed "$p" || out="$out $p"; done; echo $out; }

ask_yes() { # ask_yes "question"  (default: no)
  local a; printf "%b" "$1 [y/N] "; read -r a || return 1
  case "$a" in y|Y|yes|YES) return 0;; *) return 1;; esac
}
pause() { printf "%b" "${D}Press Enter to go back to the menu...${N}"; read -r _ || exit 0; }

apt_ready() { # refresh package lists once if they are missing
  if ! ls /var/lib/apt/lists/*Packages* >/dev/null 2>&1; then
    echo -e "${D}Package lists are empty - running apt-get update first...${N}"
    apt-get update || { echo -e "${R}FAILED: could not update the package lists. Check your internet connection.${N}"; return 1; }
  fi
  return 0
}

install_pkgs() { # install_pkgs "Name" "pkg1 pkg2"
  local name="$1" pk="$2" miss rc
  apt_ready || return 1
  miss=$(missing_of "$pk")
  if [ -z "$miss" ]; then echo -e "${G}OK: $name - everything is already installed.${N}"; return 0; fi
  echo -e "${B}Installing $name:${N} $miss"
  apt-get install -y -o Dpkg::Options::=--force-confold $miss
  rc=$?
  if all_installed "$pk"; then
    if [ $rc -eq 0 ]; then echo -e "${G}OK: $name installed.${N}"
    else echo -e "${G}OK: $name installed${N} ${D}(apt printed a warning, code $rc, but every package is in place)${N}"; fi
    return 0
  fi
  echo -e "${R}FAILED: $name was not fully installed (apt code $rc). Missing: $(missing_of "$pk")${N}"
  echo -e "${D}Tip: check your internet connection and try again, or choose 8 to update first.${N}"
  return 1
}

do_group() { # do_group number
  local n="$1" name pk
  name=$(label_of "$n"); pk=$(pkgs_of "$n")
  echo
  echo -e "${B}$name${N}: $pk"
  if [ "$n" = 6 ]; then
    echo -e "${D}These tools work without root (for example: nmap -sT). Features that need root or"
    echo -e "Wi-Fi monitor mode do not work here. Use them only on systems you own or have"
    echo -e "permission to test.${N}"
  fi
  all_installed "$pk" && { echo -e "${G}Already installed.${N}"; return 0; }
  ask_yes "Install the missing packages now?" || { echo "Cancelled. Nothing was installed."; return 0; }
  install_pkgs "$name" "$pk"
}

do_custom() {
  local line p bad=0 ok=""
  echo
  echo "Type one or more Debian package names (for example: ncdu jq ffmpeg)."
  printf "Package name(s), empty to cancel: "; read -r line || return 0
  [ -z "$line" ] && { echo "Cancelled."; return 0; }
  for p in $line; do
    if ! [[ "$p" =~ ^[a-z0-9][a-z0-9+.-]*$ ]]; then echo -e "${R}FAILED: \"$p\" is not a valid package name.${N}"; bad=1; fi
  done
  [ $bad = 1 ] && return 1
  apt_ready || return 1
  for p in $line; do
    if [ -z "$(apt-cache policy "$p" 2>/dev/null | sed -n 's/^  Candidate: //p' | grep -v '(none)')" ]; then
      echo -e "${R}FAILED: no package called \"$p\" was found (check the spelling; or choose 8 to update the lists).${N}"; return 1
    fi
  done
  echo "Will install: $line"
  ask_yes "Install now?" || { echo "Cancelled. Nothing was installed."; return 0; }
  install_pkgs "$line" "$line"
}

do_upgrade() {
  local rc
  echo
  echo "This refreshes the package lists and upgrades the installed Debian packages (can download a lot)."
  ask_yes "Update and upgrade now?" || { echo "Cancelled. Nothing was changed."; return 0; }
  echo -e "${B}Step 1/2: apt-get update${N}"
  apt-get update; rc=$?
  if [ $rc -ne 0 ]; then echo -e "${R}FAILED: update did not work (code $rc). Check your internet connection.${N}"; return 1; fi
  echo -e "${B}Step 2/2: apt-get upgrade${N}"
  apt-get upgrade -y -o Dpkg::Options::=--force-confold; rc=$?
  if [ $rc -eq 0 ]; then echo -e "${G}OK: Debian is up to date.${N}"; return 0; fi
  echo -e "${R}FAILED: upgrade ended with code $rc.${N}"; return 1
}

status_of() { local pk; pk=$(pkgs_of "$1"); if all_installed "$pk"; then echo -e "${G}[installed]${N}"; else echo -e "${D}[not installed]${N}"; fi; }

show_menu() {
  echo
  echo -e "${A}${B}DiZien tools${N} ${D}- install groups of programs with apt${N}"
  echo -e "${D}Nothing is installed until you choose it and confirm.${N}"
  echo
  local i
  for i in 1 2 3 4 5 6; do
    printf " %s) %-14s %b\n" "$i" "$(label_of $i)" "$(status_of $i)"
    echo -e "    ${D}$(pkgs_of $i)${N}"
  done
  echo " 7) Install a package by name"
  echo " 8) Update and upgrade Debian"
  echo " 0) Quit"
  echo
}

run_choice() {
  case "$1" in
    1|2|3|4|5|6) do_group "$1";;
    7) do_custom;;
    8) do_upgrade;;
    *) echo "Please type a number from the menu.";;
  esac
}

# Optional: "dizien-tools 2" runs one menu entry directly and exits.
if [ -n "$1" ]; then run_choice "$1"; exit $?; fi

while true; do
  show_menu
  printf "Choose a number: "
  read -r c || exit 0
  case "$c" in 0|q|Q|quit|exit) echo "Bye."; exit 0;; esac
  run_choice "$c"
  pause
done
