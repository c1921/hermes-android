#!/usr/bin/env bash

set -euo pipefail

asset_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_dir="$(cd "$asset_dir/../../.." && pwd)"
source_dir="$asset_dir/source"
drawable_dir="$repo_dir/app/src/main/res/drawable-nodpi"
font_dir="$repo_dir/app/src/main/res/font"
work_dir="$(mktemp -d)"

cleanup() {
    find "$work_dir" -mindepth 1 -delete
    rmdir "$work_dir"
}
trap cleanup EXIT

render_phone() {
    local source_image="$1"
    local plate_image="$2"
    local output_image="$3"
    local replace_navigation="${4:-false}"

    if [[ "$replace_navigation" == true ]]; then
        magick "$source_image" \
            -gravity north \
            -crop 1080x2180+0+0 \
            +repage \
            "$work_dir/content.png"
        magick "$source_dir/onboarding-dark.png" \
            -gravity north \
            -crop 1080x160+0+2180 \
            +repage \
            "$work_dir/navigation.png"
        magick "$work_dir/content.png" "$work_dir/navigation.png" \
            -append \
            "$work_dir/source.png"
    else
        magick "$source_image" "$work_dir/source.png"
    fi

    magick "$plate_image" \
        -resize '720x1500^' \
        -gravity center \
        -crop 720x1500+0+0 \
        +repage \
        -fill '#06194F' \
        -colorize 68 \
        "$work_dir/background.png"

    magick "$work_dir/source.png" \
        -resize '600x1300!' \
        "$work_dir/screen.png"

    magick -size 600x1300 xc:none \
        -fill white \
        -draw 'roundrectangle 0,0 599,1299 44,44' \
        "$work_dir/mask.png"

    magick "$work_dir/screen.png" "$work_dir/mask.png" \
        -alpha off \
        -compose CopyOpacity \
        -composite \
        "$work_dir/clipped.png"

    magick -size 720x1500 xc:none \
        -fill '#02081799' \
        -draw 'roundrectangle 70,110 669,1409 44,44' \
        -blur 0x26 \
        "$work_dir/shadow.png"

    magick "$work_dir/background.png" "$work_dir/shadow.png" \
        -compose Over \
        -composite \
        "$work_dir/clipped.png" \
        -geometry +60+100 \
        -compose Over \
        -composite \
        -fill none \
        -stroke '#FFE6CB99' \
        -strokewidth 3 \
        -draw 'roundrectangle 59,99 660,1400 46,46' \
        -strip \
        -define png:compression-level=9 \
        "$output_image"
}

render_phone \
    "$source_dir/onboarding-dark.png" \
    "$drawable_dir/nous_field_orbit.webp" \
    "$asset_dir/showcase-onboarding.png"

render_phone \
    "$source_dir/billing-dark.png" \
    "$drawable_dir/nous_field_portal.webp" \
    "$asset_dir/showcase-billing.png" \
    true

render_phone \
    "$source_dir/command-center-dark.png" \
    "$drawable_dir/nous_field_neural.webp" \
    "$asset_dir/showcase-command-center.png" \
    true

magick "$drawable_dir/nous_field_orbit_neural.webp" \
    -resize '1800x720^' \
    -gravity center \
    -crop 1800x720+0+0 \
    +repage \
    -fill '#06194F' \
    -colorize 64 \
    "$work_dir/banner-background.png"

magick "$drawable_dir/hermes_hero_art.webp" \
    -resize '700x880>' \
    "$work_dir/banner-art.png"

magick "$drawable_dir/hermes_app_icon.png" \
    -resize 104x104 \
    "$work_dir/banner-icon.png"

magick "$work_dir/banner-background.png" \
    "$work_dir/banner-art.png" \
    -gravity east \
    -geometry +70+18 \
    -compose Over \
    -composite \
    "$work_dir/banner-icon.png" \
    -gravity northwest \
    -geometry +112+86 \
    -compose Over \
    -composite \
    -font "$font_dir/cormorant_garamond.ttf" \
    -fill '#FFE6CB' \
    -pointsize 138 \
    -gravity northwest \
    -annotate +238+62 'HERMES' \
    -font "$font_dir/courier_prime_bold.ttf" \
    -pointsize 34 \
    -annotate +242+188 'AGENT / ANDROID' \
    -stroke '#FFE6CB66' \
    -strokewidth 2 \
    -draw 'line 112,282 944,282' \
    -stroke none \
    -font "$font_dir/cormorant_garamond.ttf" \
    -pointsize 78 \
    -annotate +112+324 'THE AGENT THAT' \
    -annotate +112+405 'GROWS WITH YOU' \
    -font "$font_dir/courier_prime_regular.ttf" \
    -pointsize 27 \
    -annotate +116+560 'NATIVE ANDROID CLIENT FOR HERMES AGENT' \
    -fill none \
    -stroke '#FFE6CB80' \
    -strokewidth 3 \
    -draw 'roundrectangle 28,28 1771,691 28,28' \
    -strip \
    -define png:compression-level=9 \
    "$asset_dir/hermes-android-banner.png"
