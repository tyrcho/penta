package towerdefense.domain

// The vault's five themes (Resources/Nature, Chaos, Loi, Mort, Science) — purely a
// presentational grouping (which faction page a building/unit/resource belongs under,
// which color/icon the UI uses for it). No gameplay logic reads this; it exists for the
// doc generator and the UI's i18n layer, not BattleEngine/CombatEngine.
enum Faction derives CanEqual:
  case Nature, Chaos, Loi, Mort, Science

object Faction:
  def of(kind: BuildingKind): Faction = kind match
    case BuildingKind.Grove | BuildingKind.Forest | BuildingKind.Jungle | BuildingKind.Stonehenge => Faction.Nature
    case BuildingKind.Cave | BuildingKind.Labyrinth                                                => Faction.Chaos
    case BuildingKind.Church | BuildingKind.Watchtower | BuildingKind.Angel                        => Faction.Loi
    case BuildingKind.Tomb | BuildingKind.BlackCastle | BuildingKind.DeathHouse | BuildingKind.PassingGate =>
      Faction.Mort
    case BuildingKind.LaboFondamental | BuildingKind.LaboNaturel | BuildingKind.LaboSombre |
        BuildingKind.LaboDeRecherche | BuildingKind.LaboDeLaLoi | BuildingKind.LaboDuChaos =>
      Faction.Science

  def of(kind: UnitKind): Faction = kind match
    case UnitKind.Elf | UnitKind.Wolf | UnitKind.Tree                     => Faction.Nature
    case UnitKind.Goblin | UnitKind.Minotaur                              => Faction.Chaos
    case UnitKind.Paladin                                                 => Faction.Loi
    case UnitKind.Zombie | UnitKind.Vampire | UnitKind.Necromancer | UnitKind.Soul => Faction.Mort

  def of(resource: Resource): Faction = resource match
    case Resource.Wood    => Faction.Nature
    case Resource.Fire    => Faction.Chaos
    case Resource.Light   => Faction.Loi
    case Resource.Shadow  => Faction.Mort
    case Resource.Crystal => Faction.Science
