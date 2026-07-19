package towerdefense.domain

// The vault's five themes (Resources/Nature, Chaos, Loi, Mort, Science) — purely a
// presentational grouping (which faction page a building/unit/resource belongs under,
// which color/icon the UI uses for it). No gameplay logic reads this; it exists for the
// doc generator and the UI's i18n layer, not BattleEngine/CombatEngine.
//
// Which faction a given BuildingKind/UnitKind/Resource belongs to is data, not a
// per-kind switch — see the `faction` field on towerdefense.domain.i18n.EntityNames'
// BuildingKindInfo/UnitKindInfo/ResourceKindInfo, defined alongside that kind's name/
// file/asset in one place.
enum Faction derives CanEqual:
  case Nature, Chaos, Loi, Mort, Science
