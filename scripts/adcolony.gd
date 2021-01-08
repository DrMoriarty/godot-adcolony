extends Node2D

var _ads = null
onready var Production = not OS.is_debug_build()
var isTop = true

func _ready():
    pause_mode = Node.PAUSE_MODE_PROCESS
    if(Engine.has_singleton("AdColony")):
        _ads = Engine.get_singleton("AdColony")
    else:
        push_warning('AdColony module not found!')
    if ProjectSettings.has_setting('AdColony/AppId'):
        var appId = ProjectSettings.get_setting('AdColony/AppId')
        var zoneId = ProjectSettings.get_setting('AdColony/ZoneId')
        init(appId, zoneId)

func init(app_id, zone_id):
    if _ads != null:
        _ads.init(app_id, zone_id, Production)

# Loaders

func loadBanner(id: String, isTop: bool, callback_id: int) -> bool:
    if _ads != null:
        _ads.loadBanner(id, isTop, callback_id)
        return true
    else:
        return false

func loadInterstitial(id: String, callback_id: int) -> bool:
    if _ads != null:
        _ads.loadInterstitial(id, callback_id)
        return true
    else:
        return false

func loadRewardedVideo(id: String, callback_id: int) -> bool:
    if _ads != null:
        _ads.loadRewardedVideo(id, callback_id)
        return true
    else:
        return false

# Check state

func bannerWidth(id: String) -> int:
    if _ads != null:
        var width = _ads.getBannerWidth(id)
        return width
    else:
        return 0

func bannerHeight(id: String) -> int:
    if _ads != null:
        var height = _ads.getBannerHeight(id)
        return height
    else:
        return 0

# Control

func showBanner(id: String) -> bool:
    if _ads != null:
        _ads.showBanner(id)
        return true
    else:
        return false

func hideBanner(id: String) -> bool:
    if _ads != null:
        _ads.hideBanner(id)
        return true
    else:
        return false

func removeBanner(id: String) -> bool:
    if _ads != null:
        _ads.removeBanner(id)
        return true
    else:
        return false

func showInterstitial(id: String) -> bool:
    if _ads != null:
        _ads.showInterstitial(id)
        return true
    else:
        return false

func showRewardedVideo(id: String) -> bool:
    if _ads != null:
        _ads.showRewardedVideo(id)
        return true
    else:
        return false

