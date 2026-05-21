"use client";

import { Eye, EyeOff, ExternalLink } from "lucide-react";
import { useState } from "react";

/**
 * Shared form primitives for the AIOMetadata panel forms. Inline-style
 * matching SourceCloud*Form.tsx but pulled into one module so the six
 * AIOMetadata forms don't each redefine the same Toggle/Select/TextField.
 */

export function Toggle({
  checked,
  onChange,
  disabled,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={`relative inline-flex h-6 w-11 items-center rounded-full transition ${
        checked ? "bg-emerald-500" : "bg-slate-600"
      } ${disabled ? "opacity-60" : ""}`}
    >
      <span
        className={`inline-block h-4 w-4 transform rounded-full bg-white transition ${
          checked ? "translate-x-6" : "translate-x-1"
        }`}
      />
    </button>
  );
}

export function ToggleRow({
  title,
  description,
  checked,
  onChange,
  disabled,
}: {
  title: string;
  description?: string;
  checked: boolean;
  onChange: (v: boolean) => void;
  disabled?: boolean;
}) {
  return (
    <div className="flex items-start justify-between gap-4 rounded-xl border border-slate-700/50 bg-slate-900/40 p-3">
      <div>
        <h3 className="text-sm font-medium text-slate-100">{title}</h3>
        {description && <p className="mt-1 text-xs text-slate-400">{description}</p>}
      </div>
      <Toggle checked={checked} onChange={onChange} disabled={disabled} />
    </div>
  );
}

export function TextField({
  label,
  value,
  onChange,
  placeholder,
  disabled,
  helpText,
  inputType,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
  disabled?: boolean;
  helpText?: string;
  inputType?: "text" | "number";
}) {
  return (
    <label className="block">
      <span className="text-xs font-medium text-slate-300">{label}</span>
      {helpText && <p className="mt-0.5 text-xs text-slate-500">{helpText}</p>}
      <input
        type={inputType ?? "text"}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        disabled={disabled}
        className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-900/60 px-3 py-2 text-sm text-slate-100 placeholder-slate-500 focus:border-emerald-400 focus:outline-none"
      />
    </label>
  );
}

export function SecretField({
  label,
  value,
  onChange,
  placeholder,
  helpText,
  helpUrl,
  helpLabel,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
  helpText?: string;
  helpUrl?: string;
  helpLabel?: string;
}) {
  const [reveal, setReveal] = useState(false);
  return (
    <div>
      <div className="flex items-baseline justify-between">
        <label className="text-xs font-medium text-slate-300">{label}</label>
        {helpUrl && (
          <a
            href={helpUrl}
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-1 text-xs text-emerald-300 hover:text-emerald-200"
          >
            <ExternalLink className="h-3 w-3" />
            {helpLabel ?? "Help"}
          </a>
        )}
      </div>
      {helpText && <p className="mt-0.5 text-xs text-slate-500">{helpText}</p>}
      <div className="mt-1.5 flex items-center gap-2">
        <input
          type={reveal ? "text" : "password"}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          placeholder={placeholder}
          autoComplete="off"
          className="w-full rounded-lg border border-slate-700 bg-slate-900/60 px-3 py-2 text-sm text-slate-100 placeholder-slate-500 focus:border-emerald-400 focus:outline-none"
        />
        <button
          type="button"
          onClick={() => setReveal((v) => !v)}
          aria-label={reveal ? "Hide" : "Show"}
          className="rounded-lg border border-slate-700 p-2 text-slate-300 hover:bg-slate-800"
        >
          {reveal ? <EyeOff className="h-3.5 w-3.5" /> : <Eye className="h-3.5 w-3.5" />}
        </button>
      </div>
    </div>
  );
}

export function Select({
  label,
  value,
  onChange,
  options,
  helpText,
  disabled,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  options: Array<{ value: string; label: string }>;
  helpText?: string;
  disabled?: boolean;
}) {
  return (
    <label className="block">
      <span className="text-xs font-medium text-slate-300">{label}</span>
      {helpText && <p className="mt-0.5 text-xs text-slate-500">{helpText}</p>}
      <select
        value={value}
        onChange={(e) => onChange(e.target.value)}
        disabled={disabled}
        className="mt-1.5 w-full rounded-lg border border-slate-700 bg-slate-900/60 px-3 py-2 text-sm text-slate-100 focus:border-emerald-400 focus:outline-none"
      >
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </label>
  );
}

export function SaveBar({
  dirty,
  isSaving,
  error,
  onSave,
  onReset,
}: {
  dirty: boolean;
  isSaving: boolean;
  error?: string | null;
  onSave: () => void;
  onReset?: () => void;
}) {
  return (
    <div className="mt-4 flex items-center justify-end gap-2">
      {error && <p className="mr-auto text-xs text-rose-300">{error}</p>}
      {onReset && (
        <button
          type="button"
          onClick={onReset}
          disabled={!dirty || isSaving}
          className="rounded-lg border border-slate-700 px-3 py-1.5 text-xs text-slate-300 hover:bg-slate-800 disabled:opacity-40"
        >
          Reset
        </button>
      )}
      <button
        type="button"
        onClick={onSave}
        disabled={!dirty || isSaving}
        className="rounded-lg bg-emerald-500/90 px-3 py-1.5 text-xs font-medium text-white hover:bg-emerald-500 disabled:opacity-50"
      >
        {isSaving ? "Saving…" : dirty ? "Save changes" : "Saved"}
      </button>
    </div>
  );
}

/** Read a string-valued setting with a default. */
export function readString(settings: Record<string, unknown>, key: string, fallback = ""): string {
  const v = settings[key];
  return typeof v === "string" ? v : fallback;
}

/** Read a boolean-valued setting with a default. */
export function readBool(settings: Record<string, unknown>, key: string, fallback = false): boolean {
  const v = settings[key];
  return typeof v === "boolean" ? v : fallback;
}

/** Read a number-valued setting with a default. */
export function readNumber(settings: Record<string, unknown>, key: string, fallback = 0): number {
  const v = settings[key];
  return typeof v === "number" ? v : fallback;
}

/** Read a nested object setting, returning an empty record if missing or wrong shape. */
export function readNested(
  settings: Record<string, unknown>,
  key: string,
): Record<string, unknown> {
  const v = settings[key];
  return v && typeof v === "object" && !Array.isArray(v)
    ? (v as Record<string, unknown>)
    : {};
}
