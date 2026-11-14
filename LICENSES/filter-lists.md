# Bundled filter lists and resources

The lists below are bundled unmodified in `app/src/main/assets/filters/` (except that uBlock Origin
`!#include` and `!#if` directives are resolved by `tools/update_filterlists.py`). Sources and sha256
values are pinned in `tools/filterlists/lists.json`.

| List | Licence | Homepage |
| --- | --- | --- |
| EasyList | GPL-3.0-or-later or CC BY-SA 3.0 | https://easylist.to/ |
| EasyPrivacy | GPL-3.0-or-later or CC BY-SA 3.0 | https://easylist.to/ |
| EasyList Cookie | GPL-3.0-or-later or CC BY-SA 3.0 | https://easylist.to/ |
| RU AdList | CC BY 3.0 (as stated in the list header) | https://forums.lanik.us/viewforum.php?f=102 |
| uBlock filters, Badware risks, Unbreak, Privacy, Annoyances | GPL-3.0 | https://github.com/uBlockOrigin/uAssets |
| AdGuard Mobile Ads filter | GPL-3.0 | https://github.com/AdguardTeam/AdguardFilters |
| AdGuard Tracking Protection filter | GPL-3.0 | https://github.com/AdguardTeam/AdguardFilters |

`app/src/main/assets/adblock/resources.json` embeds the redirect resources of uBlock Origin
(GPL-3.0), see `GPL-3.0-uBlock-resources.txt`.
