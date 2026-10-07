import { FlatESLintConfigItem, RuleConfig, MergeIntersection, EslintRules, ImportRules, NRules, UnicornRules, TypeScriptRules, VueRules, ReactRules, ReactHooksRules } from '@antfu/eslint-define-config';
import { RequiredOptions, LiteralUnion, BuiltInParserName } from 'prettier';
import { Options as Options$1 } from 'prettier-plugin-jsdoc';
import { FlatGitignoreOptions } from 'eslint-config-flat-gitignore';

type PrettierCustomParser = 'astro' | 'svelte' | 'jsdoc-parser' | 'toml';
type PrettierParser = BuiltInParserName | PrettierCustomParser;
interface PrettierOptions extends RequiredOptions {
    parser: LiteralUnion<PrettierParser>;
}
type PartialPrettierExtendedOptions = Partial<PrettierOptions> & Partial<Options$1>;
type WrapRuleConfig<T extends {
    [key: string]: any;
}> = {
    [K in keyof T]: T[K] extends RuleConfig ? T[K] : RuleConfig<T[K]>;
};
type EslintFlatRules = WrapRuleConfig<MergeIntersection<EslintRules & ImportRules & NRules & UnicornRules & TypeScriptRules & VueRules & ReactRules & ReactHooksRules>>;
type FlatConfigItem = Omit<FlatESLintConfigItem<EslintFlatRules, false>, 'plugins'> & {
    plugins?: Record<string, any>;
};
type RuleBaseOptions<T = NonNullable<unknown>> = T & {
    /** The glob patterns to lint */
    files?: string[];
};
type RequiredRuleBaseOptions = Required<RuleBaseOptions>;
type OnDemandRuleKey = 'vue' | 'react' | 'react-native' | 'solid' | 'svelte' | 'astro';
type VueOptions = RuleBaseOptions<{
    /**
     * The vue version
     *
     * @default 3
     */
    version?: 2 | 3;
}>;
type RequiredVueOptions = Required<VueOptions>;
type OnDemandRuleOptions = Partial<Record<Exclude<OnDemandRuleKey, 'vue'>, RuleBaseOptions | boolean>>;
type RequiredOnDemandRuleOptions = Record<Exclude<OnDemandRuleKey, 'vue'>, RequiredRuleBaseOptions>;

type Awaitable<T> = T | Promise<T>;
interface BaseOptions {
    /**
     * The current working directory
     *
     * @default process.cwd()
     */
    cwd: string;
    /** The globs to ignore lint */
    ignores: string[];
    /**
     * Enable gitignore support.
     *
     * Passing an object to configure the options.
     *
     * @default true
     * @see https://github.com/antfu/eslint-config-flat-gitignore
     */
    gitignore?: boolean | FlatGitignoreOptions;
    /** The override rules */
    overrides: Record<string, string>;
    /**
     * Default prettier rules
     *
     * @default
     * ```json
     * {
     *   "printWidth": 120,
     *   "singleQuote": true,
     *   "trailingComma": "none",
     *   "arrowParens": "avoid",
     *   "htmlWhitespaceSensitivity": "ignore"
     * }
     * ```
     */
    prettierRules: PartialPrettierExtendedOptions;
    /**
     * Whether to use prettierrc
     *
     * If true, the rules in prettierrc will override the default rules
     *
     * @default true
     */
    usePrettierrc: boolean;
    /**
     * @default
     * {
     *  "html": true,
     *  "css": true,
     *  "json": true,
     * }
     */
    formatter: {
        html?: boolean;
        css?: boolean;
        json?: boolean;
        markdown?: boolean;
        yaml?: boolean;
        toml?: boolean;
    };
}
type Options = Partial<BaseOptions> & {
    vue?: VueOptions | boolean;
} & OnDemandRuleOptions & {
    unocss?: boolean;
};
type ParsedOptions = BaseOptions & {
    vue?: RequiredVueOptions;
} & Partial<RequiredOnDemandRuleOptions> & {
    unocss?: boolean;
};

declare function defineConfig(options?: Partial<Options>, ...userConfigs: Awaitable<FlatConfigItem>[]): Promise<FlatConfigItem[]>;

export { type Awaitable, type BaseOptions, type FlatConfigItem, type OnDemandRuleKey, type Options, type ParsedOptions, type PartialPrettierExtendedOptions, type PrettierParser, type RequiredOnDemandRuleOptions, type RequiredRuleBaseOptions, type RequiredVueOptions, defineConfig };
