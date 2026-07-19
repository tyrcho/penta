package towerdefense.domain.i18n

// The two languages the vault doc generator and the game UI both support. Default is Fr
// (the vault's own language — see Resources/) so an unrecognized/missing saved preference
// falls back to the game's original behavior.
enum Lang derives CanEqual:
  case Fr, En

object Lang:
  def fromCode(code: String): Lang = code match
    case "en" => Lang.En
    case _    => Lang.Fr

  extension (lang: Lang)
    def code: String = lang match
      case Lang.Fr => "fr"
      case Lang.En => "en"
