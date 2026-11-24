#!/usr/bin/env bash
# Assertions the documentation build must satisfy. Run by the shared site
# workflow (jlt-commons/ci-builds) against the freshly built _site, with
# BASE_PATH exported. Locally:
#   jolt site:build && BASE_PATH=/wii-jolt bash docs/check-site.sh

set -euo pipefail
out=_site
base=${BASE_PATH:-/wii-jolt}

test -f "$out/index.html"     || { echo "no homepage generated"; exit 1; }
test -f "$out/404.html"       || { echo "no 404 page generated"; exit 1; }
test -f "$out/css/screen.css" || { echo "static assets missing"; exit 1; }

# The homepage is docs/templates/home.html. If :home-template drops out of
# site.edn, the engine falls back to the guide index and this line is gone.
grep -q 'Clojure on the Nintendo Wii.' "$out/index.html" \
  || { echo "homepage isn't the custom template"; exit 1; }

# Every guide page renders. A page that stops building would otherwise go
# unnoticed, since nothing else links to all of them.
for page in docs/guide/*.md; do
  name=$(basename "$page" .md)
  test -f "$out/guide/$name.html" || { echo "guide page $name missing"; exit 1; }
done

# The four game pictures come from docs/images through :asset-dirs.
for img in jolt-balloons-play.png jolt-mona-demo.gif starcatcher-wiimote.png shakerace-demo.png; do
  test -f "$out/images/$img" || { echo "image $img not copied"; exit 1; }
  grep -q "src=\"$base/images/$img\"" "$out/index.html" \
    || { echo "homepage doesn't link $img under $base"; exit 1; }
done

# Every link on the homepage that stays on the site names a file the build
# wrote.
bad=0
while read -r href; do
  path=${href%%#*}
  case "$path" in
    ''|http://*|https://*|mailto:*) continue ;;
    "$base"/*) file="$out/${path#"$base"/}" ;;
    "$base") file="$out/index.html" ;;
    *) file="$out/$path" ;;
  esac
  [ -d "$file" ] && file="$file/index.html"
  [ -f "$file" ] || { echo "homepage link to nothing: $href"; bad=1; }
done < <(grep -oE "href=(\"[^\"]*\"|'[^']*')" "$out/index.html" | sed -E "s/^href=.(.*).$/\1/" | sort -u)
[ "$bad" = 0 ] || exit 1

! grep -rq '{{site-base}}' "$out" || { echo "unrendered template variable"; exit 1; }

# Served at b12n-oss.github.io/wii-jolt/, a root-absolute URL outside the
# base path would load the organization site's file instead of this one's.
if grep -rohE '(href|src)="/[^"]*"' "$out" --include='*.html' | grep -v "=\"$base/" | grep -q .; then
  echo "a URL escapes $base:"
  grep -rohE '(href|src)="/[^"]*"' "$out" --include='*.html' | grep -v "=\"$base/" | sort -u | head
  exit 1
fi

echo "site checks passed"
