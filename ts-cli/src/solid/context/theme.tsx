/**
 * Theme context — 33 built-in themes with dark/light variants, KV persistence,
 * system palette detection, and full syntax-style generation for the markdown
 * renderer. Ported from MiMo-Code.
 *
 * Changes from MiMo-Code:
 *  - No plugin theme system (reserved for TuiPluginRuntime Phase 5).
 *  - No custom theme file scanning (reserved for Phase 5).
 *  - TuiThemeCurrent type is inlined (no @mimo-ai/plugin/tui dependency).
 *  - isRecord inlined (no @mimo-ai/shared dependency).
 */
import { CliRenderEvents, SyntaxStyle, RGBA, type TerminalColors } from "@opentui/core"
import path from "node:path"
import { createEffect, createMemo, onCleanup, onMount } from "solid-js"
import { createSimpleContext } from "./helper"
import { Global } from "../util/global"
import { Filesystem } from "../util/filesystem"
import { isRecord } from "../util/record"
import { useKV } from "./kv"
import { useRenderer } from "@opentui/solid"
import { createStore, produce } from "solid-js/store"

// ---------------------------------------------------------------------------
// Types (inlined from @mimo-ai/plugin/tui)
// ---------------------------------------------------------------------------

export type TuiThemeCurrent = {
  readonly primary: RGBA
  readonly secondary: RGBA
  readonly accent: RGBA
  readonly error: RGBA
  readonly warning: RGBA
  readonly success: RGBA
  readonly info: RGBA
  readonly text: RGBA
  readonly textMuted: RGBA
  readonly selectedListItemText: RGBA
  readonly background: RGBA
  readonly backgroundPanel: RGBA
  readonly backgroundElement: RGBA
  readonly backgroundMenu: RGBA
  readonly userMessageBackground: RGBA
  readonly assistantMessageBackground: RGBA
  readonly inputBackground: RGBA
  readonly border: RGBA
  readonly borderActive: RGBA
  readonly borderSubtle: RGBA
  readonly diffAdded: RGBA
  readonly diffRemoved: RGBA
  readonly diffContext: RGBA
  readonly diffHunkHeader: RGBA
  readonly diffHighlightAdded: RGBA
  readonly diffHighlightRemoved: RGBA
  readonly diffAddedBg: RGBA
  readonly diffRemovedBg: RGBA
  readonly diffContextBg: RGBA
  readonly diffLineNumber: RGBA
  readonly diffAddedLineNumberBg: RGBA
  readonly diffRemovedLineNumberBg: RGBA
  readonly markdownText: RGBA
  readonly markdownHeading: RGBA
  readonly markdownLink: RGBA
  readonly markdownLinkText: RGBA
  readonly markdownCode: RGBA
  readonly markdownBlockQuote: RGBA
  readonly markdownEmph: RGBA
  readonly markdownStrong: RGBA
  readonly markdownHorizontalRule: RGBA
  readonly markdownListItem: RGBA
  readonly markdownListEnumeration: RGBA
  readonly markdownImage: RGBA
  readonly markdownImageText: RGBA
  readonly markdownCodeBlock: RGBA
  readonly syntaxComment: RGBA
  readonly syntaxKeyword: RGBA
  readonly syntaxFunction: RGBA
  readonly syntaxVariable: RGBA
  readonly syntaxString: RGBA
  readonly syntaxNumber: RGBA
  readonly syntaxType: RGBA
  readonly syntaxOperator: RGBA
  readonly syntaxPunctuation: RGBA
  readonly thinkingOpacity: number
}

type Theme = TuiThemeCurrent & { _hasSelectedListItemText: boolean; _hasUserMessageBackground: boolean; _hasAssistantMessageBackground: boolean; _hasInputBackground: boolean }
type ThemeColor = Exclude<keyof TuiThemeCurrent, "thinkingOpacity">

// ---------------------------------------------------------------------------
// Theme JSON structure
// ---------------------------------------------------------------------------

type HexColor = `#${string}`
type RefName = string
type Variant = {
  dark: HexColor | RefName
  light: HexColor | RefName
}
type ColorValue = HexColor | RefName | Variant | RGBA
export type ThemeJson = {
  $schema?: string
  defs?: Record<string, HexColor | RefName>
  theme: Omit<Record<ThemeColor, ColorValue>, "selectedListItemText" | "backgroundMenu" | "userMessageBackground" | "assistantMessageBackground" | "inputBackground"> & {
    selectedListItemText?: ColorValue
    backgroundMenu?: ColorValue
    userMessageBackground?: ColorValue
    assistantMessageBackground?: ColorValue
    inputBackground?: ColorValue
    thinkingOpacity?: number
  }
}

// ---------------------------------------------------------------------------
// 33 built-in themes
// ---------------------------------------------------------------------------

import aura from "../theme/aura.json" with { type: "json" }
import ayu from "../theme/ayu.json" with { type: "json" }
import catppuccin from "../theme/catppuccin.json" with { type: "json" }
import catppuccinFrappe from "../theme/catppuccin-frappe.json" with { type: "json" }
import catppuccinMacchiato from "../theme/catppuccin-macchiato.json" with { type: "json" }
import cobalt2 from "../theme/cobalt2.json" with { type: "json" }
import cursor from "../theme/cursor.json" with { type: "json" }
import dracula from "../theme/dracula.json" with { type: "json" }
import everforest from "../theme/everforest.json" with { type: "json" }
import flexoki from "../theme/flexoki.json" with { type: "json" }
import github from "../theme/github.json" with { type: "json" }
import gruvbox from "../theme/gruvbox.json" with { type: "json" }
import kanagawa from "../theme/kanagawa.json" with { type: "json" }
import material from "../theme/material.json" with { type: "json" }
import matrix from "../theme/matrix.json" with { type: "json" }
import mercury from "../theme/mercury.json" with { type: "json" }
import monokai from "../theme/monokai.json" with { type: "json" }
import nightowl from "../theme/nightowl.json" with { type: "json" }
import nord from "../theme/nord.json" with { type: "json" }
import osakaJade from "../theme/osaka-jade.json" with { type: "json" }
import onedark from "../theme/one-dark.json" with { type: "json" }
import mimocode from "../theme/mimocode.json" with { type: "json" }
import jwcode from "../theme/jwcode.json" with { type: "json" }
import orng from "../theme/orng.json" with { type: "json" }
import lucentOrng from "../theme/lucent-orng.json" with { type: "json" }
import palenight from "../theme/palenight.json" with { type: "json" }
import rosepine from "../theme/rosepine.json" with { type: "json" }
import solarized from "../theme/solarized.json" with { type: "json" }
import synthwave84 from "../theme/synthwave84.json" with { type: "json" }
import tokyonight from "../theme/tokyonight.json" with { type: "json" }
import vercel from "../theme/vercel.json" with { type: "json" }
import vesper from "../theme/vesper.json" with { type: "json" }
import zenburn from "../theme/zenburn.json" with { type: "json" }
import carbonfox from "../theme/carbonfox.json" with { type: "json" }

const PLAIN_TERMINAL_THEME: ThemeJson = {
  ...mimocode,
  theme: {
    ...mimocode.theme,
    text: { dark: "darkStep12" as HexColor, light: "lightStep12" as HexColor },
    textMuted: { dark: "darkStep11" as HexColor, light: "lightStep11" as HexColor },
    background: "transparent",
    backgroundPanel: "transparent",
    backgroundElement: "transparent",
    backgroundMenu: "transparent",
    markdownText: { dark: "darkStep12" as HexColor, light: "lightStep12" as HexColor },
    markdownHeading: { dark: "darkStep12" as HexColor, light: "lightStep12" as HexColor },
    markdownStrong: { dark: "darkStep12" as HexColor, light: "lightStep12" as HexColor },
    markdownCodeBlock: { dark: "darkStep12" as HexColor, light: "lightStep12" as HexColor },
  },
}

export const DEFAULT_THEMES: Record<string, ThemeJson> = {
  aura,
  ayu,
  catppuccin,
  "catppuccin-frappe": catppuccinFrappe,
  "catppuccin-macchiato": catppuccinMacchiato,
  cobalt2,
  cursor,
  dracula,
  everforest,
  flexoki,
  github,
  gruvbox,
  kanagawa,
  material,
  matrix,
  mercury,
  monokai,
  nightowl,
  nord,
  "one-dark": onedark,
  "osaka-jade": osakaJade,
  mimocode,
  jwcode,
  orng,
  "lucent-orng": lucentOrng,
  palenight,
  rosepine,
  solarized,
  synthwave84,
  tokyonight,
  vesper,
  vercel,
  zenburn,
  carbonfox,
}

// ---------------------------------------------------------------------------
// Theme store + resolution
// ---------------------------------------------------------------------------

type State = {
  themes: Record<string, ThemeJson>
  mode: "dark" | "light"
  lock: "dark" | "light" | undefined
  active: string
  ready: boolean
}

let customThemes: Record<string, ThemeJson> = {}
let systemTheme: ThemeJson | undefined

function listThemes() {
  return { ...DEFAULT_THEMES, ...customThemes, ...(systemTheme ? { system: systemTheme } : {}) }
}

function syncThemes() {
  setStore("themes", listThemes())
}

const [store, setStore] = createStore<State>({
  themes: listThemes(),
  mode: "dark",
  lock: undefined,
  active: "jwcode",
  ready: false,
})

export function allThemes() {
  return store.themes
}

export function hasTheme(name: string) {
  if (!name) return false
  return allThemes()[name] !== undefined
}

export function addTheme(name: string, theme: unknown) {
  if (!name || !isRecord(theme) || !isRecord(theme.theme)) return false
  if (hasTheme(name)) return false
  customThemes[name] = theme as ThemeJson
  syncThemes()
  return true
}

export function upsertTheme(name: string, theme: unknown) {
  if (!name || !isRecord(theme) || !isRecord(theme.theme)) return false
  customThemes[name] = theme as ThemeJson
  syncThemes()
  return true
}

// ---------------------------------------------------------------------------
// Color resolution
// ---------------------------------------------------------------------------

export function resolveTheme(theme: ThemeJson, mode: "dark" | "light") {
  const defs = theme.defs ?? {}
  function resolveColor(c: ColorValue, chain: string[] = []): RGBA {
    if (c instanceof RGBA) return c
    if (typeof c === "string") {
      if (c === "transparent" || c === "none") return RGBA.fromInts(0, 0, 0, 0)
      if (c.startsWith("#")) return RGBA.fromHex(c)

      if (chain.includes(c)) {
        throw new Error(`Circular color reference: ${[...chain, c].join(" -> ")}`)
      }

      const next = defs[c] ?? theme.theme[c as ThemeColor]
      if (next === undefined) {
        throw new Error(`Color reference "${c}" not found in defs or theme`)
      }
      return resolveColor(next, [...chain, c])
    }
    if (typeof c === "number") {
      return ansiToRgba(c)
    }
    return resolveColor(c[mode], chain)
  }

  const resolved = Object.fromEntries(
    Object.entries(theme.theme)
      .filter(([key]) => key !== "selectedListItemText" && key !== "backgroundMenu" && key !== "thinkingOpacity" && key !== "userMessageBackground" && key !== "assistantMessageBackground" && key !== "inputBackground")
      .map(([key, value]) => {
        return [key, resolveColor(value as ColorValue)]
      }),
  ) as Partial<Record<ThemeColor, RGBA>>

  const hasSelectedListItemText = theme.theme.selectedListItemText !== undefined
  resolved.selectedListItemText = hasSelectedListItemText
    ? resolveColor(theme.theme.selectedListItemText!)
    : resolved.background

  resolved.backgroundMenu =
    theme.theme.backgroundMenu !== undefined
      ? resolveColor(theme.theme.backgroundMenu)
      : resolved.backgroundElement

  const hasUserMessageBackground = theme.theme.userMessageBackground !== undefined
  resolved.userMessageBackground = hasUserMessageBackground
    ? resolveColor(theme.theme.userMessageBackground!)
    : resolved.backgroundPanel

  const hasAssistantMessageBackground = theme.theme.assistantMessageBackground !== undefined
  resolved.assistantMessageBackground = hasAssistantMessageBackground
    ? resolveColor(theme.theme.assistantMessageBackground!)
    : resolved.backgroundElement

  const hasInputBackground = theme.theme.inputBackground !== undefined
  resolved.inputBackground = hasInputBackground
    ? resolveColor(theme.theme.inputBackground!)
    : resolved.backgroundPanel

  const thinkingOpacity = theme.theme.thinkingOpacity ?? 0.6

  return {
    ...resolved,
    _hasSelectedListItemText: hasSelectedListItemText,
    _hasUserMessageBackground: hasUserMessageBackground,
    _hasAssistantMessageBackground: hasAssistantMessageBackground,
    _hasInputBackground: hasInputBackground,
    thinkingOpacity,
  } as Theme
}

function ansiToRgba(code: number): RGBA {
  if (code < 16) {
    const ansiColors = [
      "#000000", "#800000", "#008000", "#808000",
      "#000080", "#800080", "#008080", "#c0c0c0",
      "#808080", "#ff0000", "#00ff00", "#ffff00",
      "#0000ff", "#ff00ff", "#00ffff", "#ffffff",
    ]
    return RGBA.fromHex(ansiColors[code] ?? "#000000")
  }
  if (code < 232) {
    const index = code - 16
    const b = index % 6
    const g = Math.floor(index / 6) % 6
    const r = Math.floor(index / 36)
    const val = (x: number) => (x === 0 ? 0 : x * 40 + 55)
    return RGBA.fromInts(val(r), val(g), val(b))
  }
  if (code < 256) {
    const gray = (code - 232) * 10 + 8
    return RGBA.fromInts(gray, gray, gray)
  }
  return RGBA.fromInts(0, 0, 0)
}

// ---------------------------------------------------------------------------
// Theme context provider
// ---------------------------------------------------------------------------

export function selectedForeground(theme: Theme, bg?: RGBA): RGBA {
  if (theme._hasSelectedListItemText) return theme.selectedListItemText
  if (theme.background.a === 0) {
    const targetColor = bg ?? theme.primary
    const { r, g, b } = targetColor
    const luminance = 0.299 * r + 0.587 * g + 0.114 * b
    return luminance > 0.5 ? RGBA.fromInts(0, 0, 0) : RGBA.fromInts(255, 255, 255)
  }
  return theme.background
}

/**
 * ThemeProvider init props.
 *
 * @param mode - System preferred color scheme (from OS / terminal).
 * @param plain - When true, use terminal's native colors (no jwcode backgrounds).
 * @param theme  - Optional theme name override from config (like tui.json).
 */
export const { use: useTheme, provider: ThemeProvider } = createSimpleContext({
  name: "Theme",
  init: (props: { mode: "dark" | "light"; plain?: boolean; theme?: string }) => {
    const renderer = useRenderer()
    const kv = useKV()
    const pick = (value: unknown) => {
      if (value === "dark" || value === "light") return value as "dark" | "light"
      return undefined
    }

    setStore(
      produce((draft) => {
        const lock = pick(kv.get("theme_mode_lock"))
        const mode = lock ?? props.mode
        if (!lock && pick(kv.get("theme_mode")) !== undefined) {
          kv.set("theme_mode", undefined)
        }
        draft.mode = mode
        draft.lock = lock
        const active = props.plain ? "system" : props.theme ?? kv.get("theme", "jwcode")
        draft.active = typeof active === "string" ? active : "jwcode"
        draft.ready = false
      }),
    )

    createEffect(() => {
      if (props.plain) return
      if (props.theme) setStore("active", props.theme)
    })

    function initThemes() {
      void Promise.allSettled([
        resolveSystemTheme(store.mode),
      ]).finally(() => {
        setStore("ready", true)
      })
    }

    onMount(initThemes)

    function resolveSystemTheme(mode: "dark" | "light" = store.mode) {
      return renderer
        .getPalette({ size: 16 })
        .then((colors: TerminalColors) => {
          if (!colors.palette[0]) {
            systemTheme = undefined
            syncThemes()
            if (store.active === "system") setStore("active", "jwcode")
            return
          }
          systemTheme = generateSystem(colors, mode)
          syncThemes()
        })
        .catch(() => {
          systemTheme = undefined
          syncThemes()
          if (store.active === "system") setStore("active", "jwcode")
        })
    }

    function apply(mode: "dark" | "light") {
      if (store.lock !== undefined) kv.set("theme_mode", mode)
      if (store.mode === mode) return
      setStore("mode", mode)
      renderer.clearPaletteCache()
      void resolveSystemTheme(mode)
    }

    function pin(mode: "dark" | "light" = store.mode) {
      setStore("lock", mode)
      kv.set("theme_mode_lock", mode)
      apply(mode)
    }

    function free() {
      setStore("lock", undefined)
      kv.set("theme_mode_lock", undefined)
      kv.set("theme_mode", undefined)
      const mode = renderer.themeMode
      if (mode) apply(mode)
    }

    const handle = (mode: "dark" | "light") => {
      if (store.lock) return
      apply(mode)
    }
    renderer.on(CliRenderEvents.THEME_MODE, handle)

    onCleanup(() => {
      renderer.off(CliRenderEvents.THEME_MODE, handle)
    })

    const values = createMemo(() => {
      if (props.plain) {
        const t = store.themes.system ?? PLAIN_TERMINAL_THEME
        return resolveTheme(
          {
            ...t,
            theme: {
              ...t.theme,
              background: "transparent",
              backgroundPanel: "transparent",
              backgroundElement: "transparent",
              backgroundMenu: "transparent",
            } as ThemeJson["theme"],
          },
          store.mode,
        )
      }

      const active = store.themes[store.active]
      if (active) return resolveTheme(active, store.mode)

      const saved = kv.get("theme")
      if (typeof saved === "string") {
        const t = store.themes[saved]
        if (t) return resolveTheme(t, store.mode)
      }

      return resolveTheme(store.themes.jwcode, store.mode)
    })

    createEffect(() => {
      renderer.setBackgroundColor(values().background)
    })

    const syntax = createMemo(() => generateSyntax(values()))
    const subtleSyntax = createMemo(() => generateSubtleSyntax(values()))

    return {
      theme: new Proxy(values(), {
        get(_target, prop) {
          // @ts-expect-error - dynamic prop access
          return values()[prop]
        },
      }),
      get selected() {
        return store.active
      },
      all() { return allThemes() },
      has(name: string) { return hasTheme(name) },
      syntax,
      subtleSyntax,
      mode() { return store.mode },
      locked() { return store.lock !== undefined },
      lock() { pin(store.mode) },
      unlock() { free() },
      setMode(mode: "dark" | "light") { pin(mode) },
      set(theme: string) {
        if (props.plain) return false
        if (!hasTheme(theme)) return false
        setStore("active", theme)
        kv.set("theme", theme)
        return true
      },
      get ready() { return store.ready },
    }
  },
})

// ---------------------------------------------------------------------------
// System theme generation (from terminal palette)
// ---------------------------------------------------------------------------

export function tint(base: RGBA, overlay: RGBA, alpha: number): RGBA {
  const r = base.r + (overlay.r - base.r) * alpha
  const g = base.g + (overlay.g - base.g) * alpha
  const b = base.b + (overlay.b - base.b) * alpha
  return RGBA.fromInts(Math.round(r * 255), Math.round(g * 255), Math.round(b * 255))
}

function generateSystem(colors: TerminalColors, mode: "dark" | "light"): ThemeJson {
  const bg = RGBA.fromHex(colors.defaultBackground ?? colors.palette[0]!)
  const fg = RGBA.fromHex(colors.defaultForeground ?? colors.palette[7]!)
  const transparent = RGBA.fromValues(bg.r, bg.g, bg.b, 0)
  const isDark = mode === "dark"

  const col = (i: number) => {
    const value = colors.palette[i]
    return value ? RGBA.fromHex(value) : ansiToRgba(i)
  }

  const grays = generateGrayScale(bg, isDark)
  const textMuted = generateMutedTextColor(bg, isDark)

  const ansiColors = {
    black: col(0), red: col(1), green: col(2), yellow: col(3),
    blue: col(4), magenta: col(5), cyan: col(6), white: col(7),
    redBright: col(9), greenBright: col(10),
  }

  const diffAlpha = isDark ? 0.22 : 0.14
  const diffAddedBg = tint(bg, ansiColors.green, diffAlpha)
  const diffRemovedBg = tint(bg, ansiColors.red, diffAlpha)
  const diffContextBg = grays[2]!
  const diffAddedLineNumberBg = tint(diffContextBg, ansiColors.green, diffAlpha)
  const diffRemovedLineNumberBg = tint(diffContextBg, ansiColors.red, diffAlpha)
  const diffLineNumber = textMuted

  const accentOrange = RGBA.fromInts(255, 103, 0)

  return {
    theme: {
      primary: accentOrange,
      secondary: accentOrange,
      accent: accentOrange,
      error: ansiColors.red,
      warning: ansiColors.yellow,
      success: accentOrange,
      info: accentOrange,
      text: fg,
      textMuted,
      selectedListItemText: bg,
      background: transparent,
      backgroundPanel: grays[2]!,
      backgroundElement: grays[3]!,
      backgroundMenu: grays[3]!,
      borderSubtle: grays[6]!,
      border: grays[7]!,
      borderActive: grays[8]!,
      diffAdded: ansiColors.green,
      diffRemoved: ansiColors.red,
      diffContext: grays[7]!,
      diffHunkHeader: grays[7]!,
      diffHighlightAdded: ansiColors.greenBright,
      diffHighlightRemoved: ansiColors.redBright,
      diffAddedBg,
      diffRemovedBg,
      diffContextBg,
      diffLineNumber,
      diffAddedLineNumberBg,
      diffRemovedLineNumberBg,
      markdownText: fg,
      markdownHeading: fg,
      markdownLink: ansiColors.blue,
      markdownLinkText: ansiColors.cyan,
      markdownCode: ansiColors.green,
      markdownBlockQuote: ansiColors.yellow,
      markdownEmph: ansiColors.yellow,
      markdownStrong: fg,
      markdownHorizontalRule: grays[7]!,
      markdownListItem: ansiColors.blue,
      markdownListEnumeration: ansiColors.cyan,
      markdownImage: ansiColors.blue,
      markdownImageText: ansiColors.cyan,
      markdownCodeBlock: fg,
      syntaxComment: textMuted,
      syntaxKeyword: ansiColors.magenta,
      syntaxFunction: ansiColors.blue,
      syntaxVariable: fg,
      syntaxString: ansiColors.green,
      syntaxNumber: ansiColors.yellow,
      syntaxType: ansiColors.cyan,
      syntaxOperator: ansiColors.cyan,
      syntaxPunctuation: fg,
    },
  }
}

function generateGrayScale(bg: RGBA, isDark: boolean): Record<number, RGBA> {
  const grays: Record<number, RGBA> = {}
  const bgR = bg.r * 255
  const bgG = bg.g * 255
  const bgB = bg.b * 255
  const luminance = 0.299 * bgR + 0.587 * bgG + 0.114 * bgB

  for (let i = 1; i <= 12; i++) {
    const factor = i / 12.0
    let newR: number, newG: number, newB: number

    if (isDark) {
      if (luminance < 10) {
        const gray = Math.floor(factor * 0.4 * 255)
        newR = newG = newB = gray
      } else {
        const newLum = luminance + (255 - luminance) * factor * 0.4
        const ratio = newLum / luminance
        newR = Math.min(bgR * ratio, 255)
        newG = Math.min(bgG * ratio, 255)
        newB = Math.min(bgB * ratio, 255)
      }
    } else {
      if (luminance > 245) {
        const gray = Math.floor(255 - factor * 0.4 * 255)
        newR = newG = newB = gray
      } else {
        const newLum = luminance * (1 - factor * 0.4)
        const ratio = newLum / luminance
        newR = Math.max(bgR * ratio, 0)
        newG = Math.max(bgG * ratio, 0)
        newB = Math.max(bgB * ratio, 0)
      }
    }
    grays[i] = RGBA.fromInts(Math.floor(newR), Math.floor(newG), Math.floor(newB))
  }
  return grays
}

function generateMutedTextColor(bg: RGBA, isDark: boolean): RGBA {
  const bgR = bg.r * 255
  const bgG = bg.g * 255
  const bgB = bg.b * 255
  const bgLum = 0.299 * bgR + 0.587 * bgG + 0.114 * bgB

  let grayValue: number
  if (isDark) {
    grayValue = bgLum < 10 ? 180 : Math.min(Math.floor(160 + bgLum * 0.3), 200)
  } else {
    grayValue = bgLum > 245 ? 75 : Math.max(Math.floor(100 - (255 - bgLum) * 0.2), 60)
  }
  return RGBA.fromInts(grayValue, grayValue, grayValue)
}

// ---------------------------------------------------------------------------
// Syntax style generation
// ---------------------------------------------------------------------------

function generateSyntax(theme: Theme) {
  return SyntaxStyle.fromTheme(getSyntaxRules(theme))
}

function generateSubtleSyntax(theme: Theme) {
  const rules = getSyntaxRules(theme)
  return SyntaxStyle.fromTheme(
    rules.map((rule) => {
      if (rule.style.foreground) {
        const fg = rule.style.foreground
        return {
          ...rule,
          style: {
            ...rule.style,
            foreground: RGBA.fromInts(
              Math.round(fg.r * 255),
              Math.round(fg.g * 255),
              Math.round(fg.b * 255),
              Math.round(theme.thinkingOpacity * 255),
            ),
          },
        }
      }
      return rule
    }),
  )
}

function getSyntaxRules(theme: Theme) {
  // Rule factory helpers
  const r = (scope: string[], style: { foreground: RGBA; background?: RGBA; bold?: boolean; italic?: boolean; underline?: boolean }) => ({ scope, style })

  return [
    r(["default"], { foreground: theme.text }),
    r(["prompt"], { foreground: theme.accent }),
    r(["extmark.file"], { foreground: theme.warning, bold: true }),
    r(["extmark.agent"], { foreground: theme.secondary, bold: true }),
    r(["extmark.paste"], { foreground: theme.background, background: theme.warning, bold: true }),
    r(["comment"], { foreground: theme.syntaxComment, italic: true }),
    r(["comment.documentation"], { foreground: theme.syntaxComment, italic: true }),
    r(["string", "symbol"], { foreground: theme.syntaxString }),
    r(["number", "boolean"], { foreground: theme.syntaxNumber }),
    r(["character.special"], { foreground: theme.syntaxString }),
    r(["keyword.return", "keyword.conditional", "keyword.repeat", "keyword.coroutine"], { foreground: theme.syntaxKeyword, italic: true }),
    r(["keyword.type"], { foreground: theme.syntaxType, bold: true, italic: true }),
    r(["keyword.function", "function.method"], { foreground: theme.syntaxFunction }),
    r(["keyword"], { foreground: theme.syntaxKeyword, italic: true }),
    r(["keyword.import"], { foreground: theme.syntaxKeyword }),
    r(["operator", "keyword.operator", "punctuation.delimiter"], { foreground: theme.syntaxOperator }),
    r(["keyword.conditional.ternary"], { foreground: theme.syntaxOperator }),
    r(["variable", "variable.parameter", "function.method.call", "function.call"], { foreground: theme.syntaxVariable }),
    r(["variable.member", "function", "constructor"], { foreground: theme.syntaxFunction }),
    r(["type", "module"], { foreground: theme.syntaxType }),
    r(["constant"], { foreground: theme.syntaxNumber }),
    r(["property"], { foreground: theme.syntaxVariable }),
    r(["class"], { foreground: theme.syntaxType }),
    r(["parameter"], { foreground: theme.syntaxVariable }),
    r(["punctuation", "punctuation.bracket"], { foreground: theme.syntaxPunctuation }),
    r(["variable.builtin", "type.builtin", "function.builtin", "module.builtin", "constant.builtin"], { foreground: theme.error }),
    r(["variable.super"], { foreground: theme.error }),
    r(["string.escape", "string.regexp"], { foreground: theme.syntaxKeyword }),
    r(["keyword.directive"], { foreground: theme.syntaxKeyword, italic: true }),
    r(["punctuation.special"], { foreground: theme.syntaxOperator }),
    r(["keyword.modifier"], { foreground: theme.syntaxKeyword, italic: true }),
    r(["keyword.exception"], { foreground: theme.syntaxKeyword, italic: true }),
    r(["markup.heading", "markup.heading.1", "markup.heading.2", "markup.heading.3", "markup.heading.4", "markup.heading.5", "markup.heading.6"], { foreground: theme.markdownHeading, bold: true }),
    r(["markup.bold", "markup.strong"], { foreground: theme.markdownStrong, bold: true }),
    r(["markup.italic"], { foreground: theme.markdownEmph, italic: true }),
    r(["markup.list"], { foreground: theme.markdownListItem }),
    r(["markup.quote"], { foreground: theme.markdownBlockQuote, italic: true }),
    r(["markup.raw", "markup.raw.block"], { foreground: theme.markdownCode }),
    r(["markup.raw.inline"], { foreground: theme.markdownCode, background: theme.background }),
    r(["markup.link"], { foreground: theme.markdownLink, underline: true }),
    r(["markup.link.label"], { foreground: theme.markdownLinkText, underline: true }),
    r(["markup.link.url"], { foreground: theme.markdownLink, underline: true }),
    r(["label"], { foreground: theme.markdownLinkText }),
    r(["spell", "nospell"], { foreground: theme.text }),
    r(["conceal"], { foreground: theme.textMuted }),
    r(["string.special", "string.special.url"], { foreground: theme.markdownLink, underline: true }),
    r(["character"], { foreground: theme.syntaxString }),
    r(["float"], { foreground: theme.syntaxNumber }),
    r(["comment.error"], { foreground: theme.error, italic: true, bold: true }),
    r(["comment.warning"], { foreground: theme.warning, italic: true, bold: true }),
    r(["comment.todo", "comment.note"], { foreground: theme.info, italic: true, bold: true }),
    r(["namespace"], { foreground: theme.syntaxType }),
    r(["field"], { foreground: theme.syntaxVariable }),
    r(["type.definition"], { foreground: theme.syntaxType, bold: true }),
    r(["keyword.export"], { foreground: theme.syntaxKeyword }),
    r(["attribute", "annotation"], { foreground: theme.warning }),
    r(["tag"], { foreground: theme.error }),
    r(["tag.attribute"], { foreground: theme.syntaxKeyword }),
    r(["tag.delimiter"], { foreground: theme.syntaxOperator }),
    r(["markup.strikethrough"], { foreground: theme.textMuted }),
    r(["markup.underline"], { foreground: theme.text, underline: true }),
    r(["markup.list.checked"], { foreground: theme.success }),
    r(["markup.list.unchecked"], { foreground: theme.textMuted }),
    r(["diff.plus"], { foreground: theme.diffAdded, background: theme.diffAddedBg }),
    r(["diff.minus"], { foreground: theme.diffRemoved, background: theme.diffRemovedBg }),
    r(["diff.delta"], { foreground: theme.diffContext, background: theme.diffContextBg }),
    r(["error"], { foreground: theme.error, bold: true }),
    r(["warning"], { foreground: theme.warning, bold: true }),
    r(["info"], { foreground: theme.info }),
    r(["debug"], { foreground: theme.textMuted }),
  ]
}

// ---------------------------------------------------------------------------
// Built-in theme registry (for tests + theme picker UI)
// ---------------------------------------------------------------------------

/** Internal: id → ThemeJson mapping for every bundled theme JSON. */
const BUILTIN_THEMES: Record<string, ThemeJson> = {
  aura,
  ayu,
  catppuccin,
  "catppuccin-frappe": catppuccinFrappe,
  "catppuccin-macchiato": catppuccinMacchiato,
  cobalt2,
  cursor,
  dracula,
  everforest,
  flexoki,
  github,
  gruvbox,
  kanagawa,
  material,
  matrix,
  mercury,
  monokai,
  nightowl,
  nord,
  "osaka-jade": osakaJade,
  "one-dark": onedark,
  mimocode,
  jwcode,
  orng,
  "lucent-orng": lucentOrng,
  palenight,
  rosepine,
  solarized,
  synthwave84,
  tokyonight,
  vercel,
  vesper,
  zenburn,
  carbonfox,
}

/** Returns the registered built-in theme JSON by id (filename without `.json`).
 *  Exported for unit tests; the picker UI may also consume this. */
export function getBuiltinTheme(id: string): ThemeJson | undefined {
  return BUILTIN_THEMES[id]
}

/** Returns all built-in theme ids (e.g. `["aura", "ayu", "catppuccin", ...]`).
 *  Exported for unit tests and theme picker UI. */
export function listBuiltinThemes(): string[] {
  return Object.keys(BUILTIN_THEMES)
}
