package towerdefense.domain.i18n

// The game's static UI chrome text (control bar buttons, stat/progress labels, game-over
// banner) — everything GameApp.scala writes into index.html's DOM that isn't a
// building/unit-specific tooltip (see TooltipText for those). Kept separate from
// EntityNames/TooltipText since this is pure interface copy, not vault-derived content.
object Ui:
  def pause(lang: Lang): String = lang match
    case Lang.Fr => "Pause"
    case Lang.En => "Pause"

  def play(lang: Lang): String = lang match
    case Lang.Fr => "Lecture"
    case Lang.En => "Play"

  def paused(lang: Lang): String = lang match
    case Lang.Fr => "En pause"
    case Lang.En => "Paused"

  def fullscreen(lang: Lang): String = lang match
    case Lang.Fr => "Plein écran"
    case Lang.En => "Full"

  def exitFullscreen(lang: Lang): String = lang match
    case Lang.Fr => "Quitter"
    case Lang.En => "Exit"

  def newGame(lang: Lang): String = lang match
    case Lang.Fr => "Nouvelle partie"
    case Lang.En => "New Game"

  def aiLabel(lang: Lang): String = lang match
    case Lang.Fr => "IA :"
    case Lang.En => "AI:"

  def spectateLabel(leftName: String, rightName: String, lang: Lang): String = lang match
    case Lang.Fr => s"Duel IA : $leftName vs $rightName"
    case Lang.En => s"AI duel: $leftName vs $rightName"

  def wood(lang: Lang): String = EntityNames.resourceName(towerdefense.domain.Resource.Wood, lang)
  def fire(lang: Lang): String = EntityNames.resourceName(towerdefense.domain.Resource.Fire, lang)
  def light(lang: Lang): String = EntityNames.resourceName(towerdefense.domain.Resource.Light, lang)
  def shadow(lang: Lang): String = EntityNames.resourceName(towerdefense.domain.Resource.Shadow, lang)
  def crystal(lang: Lang): String = EntityNames.resourceName(towerdefense.domain.Resource.Crystal, lang)

  def natureTitle(lang: Lang): String = lang match
    case Lang.Fr => "Nature"
    case Lang.En => "Nature"
  def chaosTitle(lang: Lang): String = "Chaos"
  def loiTitle(lang: Lang): String = lang match
    case Lang.Fr => "Loi"
    case Lang.En => "Law"
  def mortTitle(lang: Lang): String = lang match
    case Lang.Fr => "Mort"
    case Lang.En => "Death"
  def scienceTitle(lang: Lang): String = "Science"

  def forestsLabel(lang: Lang): String = lang match
    case Lang.Fr => "Forêts"
    case Lang.En => "Forests"

  def plunderedLabel(lang: Lang): String = lang match
    case Lang.Fr => "Pillé"
    case Lang.En => "Plundered"

  def corruptedLabel(lang: Lang): String = lang match
    case Lang.Fr => "Corrompu"
    case Lang.En => "Corrupted"

  def victoryLabel(lang: Lang): String = lang match
    case Lang.Fr => "Victoire"
    case Lang.En => "Victory"

  def fondamentaleLabel(lang: Lang): String = lang match
    case Lang.Fr => "Fondamentale"
    case Lang.En => "Fondamentale"

  def won(lang: Lang): String = lang match
    case Lang.Fr => "GAGNÉ"
    case Lang.En => "WON"

  def lost(lang: Lang): String = lang match
    case Lang.Fr => "PERDU"
    case Lang.En => "LOST"

  // The language toggle button itself always shows the *other* language's own name, i.e.
  // what clicking it switches to — same convention most bilingual sites use.
  def languageToggleLabel(current: Lang): String = current match
    case Lang.Fr => "EN"
    case Lang.En => "FR"
