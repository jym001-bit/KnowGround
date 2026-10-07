"use strict";
var __create = Object.create;
var __defProp = Object.defineProperty;
var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
var __getOwnPropNames = Object.getOwnPropertyNames;
var __getProtoOf = Object.getPrototypeOf;
var __hasOwnProp = Object.prototype.hasOwnProperty;
var __export = (target, all) => {
  for (var name in all)
    __defProp(target, name, { get: all[name], enumerable: true });
};
var __copyProps = (to, from, except, desc) => {
  if (from && typeof from === "object" || typeof from === "function") {
    for (let key of __getOwnPropNames(from))
      if (!__hasOwnProp.call(to, key) && key !== except)
        __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
  }
  return to;
};
var __toESM = (mod, isNodeMode, target) => (target = mod != null ? __create(__getProtoOf(mod)) : {}, __copyProps(
  // If the importer is in node compatibility mode or this is not an ESM
  // file that has been converted to a CommonJS file using a Babel-
  // compatible transform (i.e. "__esModule" has not been set), then set
  // "default" to the CommonJS "module.exports" for node compatibility.
  isNodeMode || !mod || !mod.__esModule ? __defProp(target, "default", { value: mod, enumerable: true }) : target,
  mod
));
var __toCommonJS = (mod) => __copyProps(__defProp({}, "__esModule", { value: true }), mod);

// src/index.ts
var index_exports = {};
__export(index_exports, {
  defineConfig: () => defineConfig
});
module.exports = __toCommonJS(index_exports);

// src/shared/index.ts
var import_node_process = __toESM(require("process"), 1);
var import_promises = require("fs/promises");
var import_node_path = __toESM(require("path"), 1);
var import_local_pkg = require("local-pkg");
async function interopDefault(m) {
  const resolved = await m;
  return resolved.default || resolved;
}
async function ensurePackages(packages) {
  if (import_node_process.default.env.CI || import_node_process.default.stdout.isTTY === false) return;
  const nonExistingPackages = packages.filter((i) => !(0, import_local_pkg.isPackageExists)(i));
  if (nonExistingPackages.length === 0) return;
  const { default: prompts } = await import("prompts");
  const message = `${nonExistingPackages.length === 1 ? "Package is" : "Packages are"} required for this config: ${nonExistingPackages.join(", ")}. Do you want to install them?`;
  const { result } = await prompts([
    {
      message,
      name: "result",
      type: "confirm"
    }
  ]);
  if (result) {
    const { installPackage } = await import("@antfu/install-pkg");
    await installPackage(nonExistingPackages, { dev: true });
  }
}
async function loadPrettierConfig(cwd) {
  let prettierConfig = {};
  try {
    const prettierrc = await (0, import_promises.readFile)(import_node_path.default.join(cwd, ".prettierrc"), "utf-8");
    prettierConfig = JSON.parse(prettierrc);
  } catch {
  }
  return prettierConfig;
}
function getOverridesRules(overrides = {}) {
  const overrideRecord = {
    js: {}
  };
  const rulePrefixes = {
    ts: "@typescript-eslint/",
    import: "import/",
    n: "n/",
    unicorn: "unicorn/",
    vue: "vue/",
    "react-native": "react-native/",
    react: "react/",
    astro: "astro/",
    svelte: "svelte/",
    solid: "solid/",
    unocss: "unocss/"
  };
  const overrideRuleKeys = Object.keys(rulePrefixes);
  overrideRuleKeys.forEach((key) => {
    overrideRecord[key] = {};
  });
  const ruleKeys = Object.keys(overrides);
  ruleKeys.forEach((key) => {
    const hasMatch = overrideRuleKeys.some((overrideKey) => {
      const prefix = rulePrefixes[overrideKey];
      const matched = key.startsWith(prefix);
      if (matched) {
        overrideRecord[overrideKey][key] = overrides[key];
      }
      return matched;
    });
    if (!hasMatch) {
      overrideRecord.js[key] = overrides[key];
    }
  });
  return overrideRecord;
}

// src/configs/gitignore.ts
async function createGitignoreRule(options) {
  if (!options) return [];
  const configs = [];
  const configItem = await interopDefault(import("eslint-config-flat-gitignore")).then((r) => [
    r(typeof options !== "boolean" ? options : { strict: false })
  ]);
  configs.push(...configItem);
  return configs;
}

// src/configs/javascript.ts
var import_globals = __toESM(require("globals"), 1);
var import_js = __toESM(require("@eslint/js"), 1);

// src/constants/glob.ts
var GLOB_SRC_EXT = "?([cm])[jt]s?(x)";
var GLOB_SRC = "**/*.?([cm])[jt]s?(x)";
var GLOB_JSX = "**/*.?([cm])jsx";
var GLOB_TSX = "**/*.?([cm])tsx";
var GLOB_VUE = "**/*.vue";
var GLOB_ASTRO = "**/*.astro";
var GLOB_SVELTE = "**/*.svelte";
var GLOB_HTML = "**/*.htm?(l)";
var GLOB_CSS = "**/*.css";
var GLOB_POSTCSS = "**/*.{p,post}css";
var GLOB_LESS = "**/*.less";
var GLOB_SCSS = "**/*.scss";
var GLOB_JSON = "**/*.json";
var GLOB_JSON5 = "**/*.json5";
var GLOB_JSONC = "**/*.jsonc";
var GLOB_MARKDOWN = "**/*.md";
var GLOB_YAML = "**/*.y?(a)ml";
var GLOB_TOML = "**/*.toml";
var GLOB_PRETTIER_LINT = [GLOB_SRC, GLOB_VUE];
var GLOB_TESTS = [
  `**/__tests__/**/*.${GLOB_SRC_EXT}`,
  `**/*.spec.${GLOB_SRC_EXT}`,
  `**/*.test.${GLOB_SRC_EXT}`,
  `**/*.bench.${GLOB_SRC_EXT}`,
  `**/*.benchmark.${GLOB_SRC_EXT}`
];
var GLOB_EXCLUDE = [
  "**/node_modules",
  "**/dist",
  "**/package-lock.json",
  "**/yarn.lock",
  "**/pnpm-lock.yaml",
  "**/bun.lockb",
  "**/output",
  "**/coverage",
  "**/temp",
  "**/.temp",
  "**/tmp",
  "**/.tmp",
  "**/.history",
  "**/.vitepress/cache",
  "**/.nuxt",
  "**/.next",
  "**/.vercel",
  "**/.changeset",
  "**/.idea",
  "**/.cache",
  "**/.output",
  "**/.vite-inspect",
  "**/CHANGELOG*.md",
  "**/*.min.*",
  "**/LICENSE*",
  "**/__snapshots__",
  "**/auto-import?(s).d.ts",
  "**/components.d.ts",
  "**/.github/workflows/*.yml"
];

// src/configs/javascript.ts
function createJsConfig(overrides = {}) {
  const js = [
    {
      languageOptions: {
        ecmaVersion: 2023,
        globals: {
          ...import_globals.default.browser,
          ...import_globals.default.es2025,
          ...import_globals.default.node,
          document: "readonly",
          navigator: "readonly",
          window: "readonly"
        },
        parserOptions: {
          ecmaFeatures: {
            jsx: true
          },
          ecmaVersion: 2022,
          sourceType: "module"
        },
        sourceType: "module"
      },
      linterOptions: {
        reportUnusedDisableDirectives: false
      },
      rules: {
        ...import_js.default.configs.all.rules,
        "accessor-pairs": ["error", { enforceForClassMembers: true, setWithoutGet: true }],
        camelcase: "off",
        "capitalized-comments": "off",
        "dot-notation": ["error", { allowKeywords: true }],
        "func-style": "off",
        "id-length": "off",
        "init-declarations": "off",
        "line-comment-position": "off",
        "max-classes-per-file": "off",
        "max-lines": "off",
        "max-lines-per-function": "off",
        "max-statements": "off",
        "max-statements-per-line": "off",
        "multiline-comment-style": "off",
        "new-cap": ["error", { newIsCap: true, capIsNew: false, properties: true }],
        "no-console": "warn",
        "no-duplicate-imports": "off",
        "no-empty": ["error", { allowEmptyCatch: true }],
        "no-empty-function": "off",
        "no-inline-comments": "off",
        "no-labels": ["error", { allowLoop: false, allowSwitch: false }],
        "no-magic-numbers": "off",
        "no-mixed-operators": [
          "error",
          {
            groups: [
              ["+", "-", "*", "/", "%", "**"],
              ["&", "|", "^", "~", "<<", ">>", ">>>"],
              ["==", "!=", "===", "!==", ">", ">=", "<", "<="],
              ["&&", "||"],
              ["in", "instanceof"]
            ],
            allowSamePrecedence: true
          }
        ],
        "no-negated-condition": "off",
        "no-redeclare": ["error", { builtinGlobals: false }],
        "no-restricted-globals": [
          "error",
          { message: "Use `globalThis` instead.", name: "global" },
          { message: "Use `globalThis` instead.", name: "self" }
        ],
        "no-restricted-properties": [
          "error",
          {
            message: "Use `Object.getPrototypeOf` or `Object.setPrototypeOf` instead.",
            property: "__proto__"
          },
          {
            message: "Use `Object.defineProperty` instead.",
            property: "__defineGetter__"
          },
          {
            message: "Use `Object.defineProperty` instead.",
            property: "__defineSetter__"
          },
          {
            message: "Use `Object.getOwnPropertyDescriptor` instead.",
            property: "__lookupGetter__"
          },
          {
            message: "Use `Object.getOwnPropertyDescriptor` instead.",
            property: "__lookupSetter__"
          }
        ],
        "no-restricted-syntax": [
          "error",
          "DebuggerStatement",
          "LabeledStatement",
          "WithStatement",
          "TSEnumDeclaration[const=true]",
          "TSExportAssignment"
        ],
        "no-self-assign": ["error", { props: true }],
        "no-ternary": "off",
        "no-undefined": "off",
        "no-unneeded-ternary": ["error", { defaultAssignment: false }],
        "no-unused-expressions": [
          "error",
          {
            allowShortCircuit: true,
            allowTaggedTemplates: true,
            allowTernary: true
          }
        ],
        "no-unused-vars": [
          "error",
          {
            vars: "all",
            args: "all",
            ignoreRestSiblings: false,
            varsIgnorePattern: "^_",
            argsIgnorePattern: "^_"
          }
        ],
        "no-useless-assignment": "off",
        "no-use-before-define": ["error", { functions: false, classes: false, variables: true }],
        "object-shorthand": [
          "error",
          "always",
          {
            ignoreConstructors: false,
            avoidQuotes: true
          }
        ],
        "one-var": ["error", "never"],
        "prefer-arrow-callback": [
          "error",
          {
            allowNamedFunctions: false,
            allowUnboundThis: true
          }
        ],
        "prefer-const": [
          "error",
          {
            destructuring: "all",
            ignoreReadBeforeAssign: true
          }
        ],
        "prefer-destructuring": "off",
        "prefer-named-capture-group": "off",
        "prefer-regex-literals": ["error", { disallowRedundantWrapping: true }],
        "require-atomic-updates": "off",
        "require-await": "off",
        "require-unicode-regexp": "off",
        "sort-imports": [
          "error",
          {
            allowSeparatedGroups: false,
            ignoreCase: false,
            ignoreDeclarationSort: true,
            ignoreMemberSort: false,
            memberSyntaxSortOrder: ["none", "all", "multiple", "single"]
          }
        ],
        "sort-keys": "off",
        "spaced-comment": [
          "error",
          "always",
          {
            line: { markers: ["*package", "!", "/", ",", "="] },
            block: {
              balanced: true,
              markers: ["*package", "!", ",", ":", "::", "flow-include"],
              exceptions: ["*"]
            }
          }
        ],
        "unicode-bom": ["error", "never"],
        ...overrides
      }
    },
    {
      files: [`**/scripts/${GLOB_SRC}`, `**/cli.${GLOB_SRC_EXT}`],
      rules: {
        "no-console": "off"
      }
    },
    {
      files: GLOB_TESTS,
      rules: {
        "no-unused-expressions": "off"
      }
    }
  ];
  return js;
}

// src/configs/node.ts
async function createNodeConfig(overrides = {}) {
  const pluginNode = await interopDefault(import("eslint-plugin-n"));
  const configs = [
    {
      plugins: {
        n: pluginNode
      },
      rules: {
        "n/handle-callback-err": ["error", "^(err|error)$"],
        "n/no-deprecated-api": "error",
        "n/no-exports-assign": "error",
        "n/no-new-require": "error",
        "n/no-path-concat": "error",
        "n/prefer-global/buffer": ["error", "never"],
        "n/prefer-global/process": ["error", "never"],
        "n/process-exit-as-throw": "error",
        ...overrides
      }
    }
  ];
  return configs;
}

// src/configs/import.ts
async function createImportConfig(overrides = {}) {
  const pluginImport = await interopDefault(import("eslint-plugin-import-x"));
  const externalVue = [
    "vite",
    "vite/**",
    "vitepress",
    "vitepress/**",
    "@vitejs/**",
    "vite-plugin-**",
    "vue",
    "@vue/**",
    "vue-router",
    "@vueuse/**",
    "unocss",
    "@unocss/**",
    "unplugin-**",
    "pinia",
    "naive-ui",
    "element-plus",
    "ant-design-vue",
    "soy-ui",
    "@soy-ui/**",
    "soybean-ui",
    "@soybean-ui/**",
    "@soybeanjs/**"
  ];
  const externalReact = ["react", "react-dom", "react-router-dom", "react-query", "react-i18next", "antd"];
  const externalCommon = ["axios", "es-toolkit", "date-fns", "dayjs", "lodash-es", "@tanstack/**", "zod", "valibot"];
  const internals = [
    "@/constant",
    "@/constant/**",
    "@/constants",
    "@/constants/**",
    "@/config",
    "@/config/**",
    "@/configs",
    "@/configs/**",
    "@/setting",
    "@/setting/**",
    "@/settings",
    "@/settings/**",
    "@/plugin",
    "@/plugin/**",
    "@/plugins",
    "@/plugins/**",
    "@/layouts",
    "@/views",
    "@/components",
    "@/router",
    "@/service",
    "@/service/**",
    "@/services",
    "@/services/**",
    "@/api",
    "@/api/**",
    "@/apis",
    "@/apis/**",
    "@/store",
    "@/store/**",
    "@/context",
    "@/context/**",
    "@/composables",
    "@/composables/**",
    "@/hooks",
    "@/hooks/**",
    "@/directives",
    "@/shared",
    "@/shared/**",
    "@/utils",
    "@/utils/**",
    "@/styles",
    "@/style",
    "@/assets",
    "@/assets/**",
    "@/**"
  ];
  const externalGroups = [...externalVue, ...externalReact, ...externalCommon].map((item) => ({
    pattern: item,
    group: "external",
    position: "before"
  }));
  const internalGroups = internals.map((item) => ({
    pattern: item,
    group: "internal",
    position: "before"
  }));
  const configs = [
    {
      plugins: {
        import: pluginImport
      },
      rules: {
        "import/first": "error",
        "import/no-duplicates": "error",
        "import/no-mutable-exports": "error",
        "import/no-named-default": "error",
        "import/no-self-import": "error",
        "import/no-webpack-loader-syntax": "error",
        "import/order": [
          "error",
          {
            "newlines-between": "never",
            groups: ["builtin", "external", "internal", "parent", "sibling", "index"],
            pathGroups: [...externalGroups, ...internalGroups],
            pathGroupsExcludedImportTypes: []
          }
        ],
        ...overrides
      }
    }
  ];
  return configs;
}

// src/configs/unicorn.ts
async function createUnicornConfig(overrides = {}) {
  const pluginUnicorn = await interopDefault(import("eslint-plugin-unicorn"));
  const configs = [
    {
      plugins: {
        unicorn: pluginUnicorn
      },
      rules: {
        "unicorn/error-message": "error",
        "unicorn/escape-case": "error",
        "unicorn/no-instanceof-array": "error",
        "unicorn/no-new-array": "error",
        "unicorn/no-new-buffer": "error",
        "unicorn/number-literal-case": "error",
        "unicorn/prefer-dom-node-text-content": "error",
        "unicorn/prefer-includes": "error",
        "unicorn/prefer-node-protocol": "error",
        "unicorn/prefer-number-properties": "error",
        "unicorn/prefer-string-starts-ends-with": "error",
        "unicorn/prefer-type-error": "error",
        "unicorn/throw-new-error": "error",
        ...overrides
      }
    }
  ];
  return configs;
}

// src/configs/typescript.ts
async function createTsRules() {
  const pluginTs = await interopDefault(import("@typescript-eslint/eslint-plugin"));
  const { rules: recommendedRules } = pluginTs.configs["eslint-recommended"].overrides[0];
  const tsRules = {
    ...pluginTs.configs.base.rules,
    ...recommendedRules,
    ...pluginTs.configs.strict.rules,
    "@typescript-eslint/consistent-type-imports": ["error", { prefer: "type-imports", disallowTypeAnnotations: false }],
    "@typescript-eslint/no-empty-interface": [
      "error",
      {
        allowSingleExtends: true
      }
    ],
    // Override JS
    "no-redeclare": "off",
    "@typescript-eslint/no-redeclare": "error",
    "no-unused-vars": "off",
    "@typescript-eslint/no-unused-vars": [
      "error",
      {
        vars: "all",
        args: "all",
        ignoreRestSiblings: false,
        varsIgnorePattern: "^_",
        argsIgnorePattern: "^_"
      }
    ],
    "no-use-before-define": "off",
    "@typescript-eslint/no-use-before-define": ["error", { functions: false, classes: false, variables: true }],
    "no-shadow": "off",
    "@typescript-eslint/no-shadow": "error",
    // off
    "@typescript-eslint/ban-types": "off",
    "@typescript-eslint/consistent-type-definitions": "off",
    "@typescript-eslint/no-empty-function": "off",
    "@typescript-eslint/no-explicit-any": "off",
    "@typescript-eslint/no-non-null-assertion": "off",
    "@typescript-eslint/unified-signatures": "off"
  };
  return tsRules;
}
async function createTsConfig(overrides = {}) {
  const [pluginTs, parserTs] = await Promise.all([
    interopDefault(import("@typescript-eslint/eslint-plugin")),
    interopDefault(import("@typescript-eslint/parser"))
  ]);
  const tsRules = await createTsRules();
  const ts = [
    {
      files: [GLOB_SRC],
      languageOptions: {
        parser: parserTs,
        parserOptions: {
          sourceType: "module"
        }
      },
      plugins: {
        "@typescript-eslint": pluginTs
      },
      rules: {
        ...tsRules,
        ...overrides
      }
    },
    {
      files: ["**/*.js", "**/*.cjs"],
      rules: {
        "@typescript-eslint/no-require-imports": "off",
        "@typescript-eslint/no-var-requires": "off"
      }
    }
  ];
  return ts;
}

// src/configs/vue.ts
async function createVueConfig(options, overrides = {}) {
  if (!options) return [];
  const { version, files } = options;
  await ensurePackages(["eslint-plugin-vue", "vue-eslint-parser"]);
  const [pluginVue, parserVue, pluginTs] = await Promise.all([
    interopDefault(import("eslint-plugin-vue")),
    interopDefault(import("vue-eslint-parser")),
    interopDefault(import("@typescript-eslint/eslint-plugin"))
  ]);
  const tsRules = await createTsRules();
  const configKeys = version === 3 ? ["essential", "strongly-recommended", "recommended"] : ["vue2-essential", "vue2-strongly-recommended", "vue2-recommended"];
  const vueRules = configKeys.reduce((preRules, key) => {
    const config = pluginVue.configs[key];
    return {
      ...preRules,
      ...config.rules
    };
  }, {});
  const configs = [
    {
      plugins: {
        vue: pluginVue
      }
    },
    {
      files,
      languageOptions: {
        parser: parserVue,
        parserOptions: {
          ecmaFeatures: {
            jsx: true
          },
          extraFileExtensions: [".vue"],
          parser: "@typescript-eslint/parser",
          sourceType: "module"
        }
      },
      processor: pluginVue.processors[".vue"],
      plugins: {
        "@typescript-eslint": pluginTs
      },
      rules: {
        ...tsRules,
        ...pluginVue.configs.base.rules,
        ...vueRules,
        "vue/block-order": ["warn", { order: ["script", "template", "style"] }],
        "vue/component-api-style": ["warn", ["script-setup", "composition"]],
        "vue/component-name-in-template-casing": [
          "warn",
          "PascalCase",
          { registeredComponentsOnly: false, ignores: [] }
        ],
        "vue/component-options-name-casing": ["warn", "PascalCase"],
        "vue/custom-event-name-casing": ["warn", "camelCase"],
        "vue/define-emits-declaration": ["warn", "type-based"],
        "vue/define-macros-order": "off",
        // 'vue/define-macros-order': [
        //   'warn',
        //   {
        //     order: ['defineOptions', 'defineProps', 'defineEmits', 'defineSlots']
        //   }
        // ],
        "vue/define-props-declaration": ["warn", "type-based"],
        "vue/html-comment-content-newline": "warn",
        "vue/multi-word-component-names": "warn",
        "vue/next-tick-style": ["warn", "promise"],
        "vue/no-duplicate-attr-inheritance": "warn",
        "vue/no-required-prop-with-default": "warn",
        "vue/no-static-inline-styles": "warn",
        "vue/no-template-target-blank": "error",
        "vue/no-this-in-before-route-enter": "error",
        "vue/no-undef-properties": "warn",
        "vue/no-unsupported-features": "warn",
        "vue/no-unused-emit-declarations": "warn",
        "vue/no-unused-properties": "warn",
        "vue/no-unused-refs": "warn",
        "vue/no-use-v-else-with-v-for": "error",
        "vue/no-useless-mustaches": "warn",
        "vue/no-useless-v-bind": "error",
        "vue/no-v-text": "warn",
        "vue/padding-line-between-blocks": "warn",
        "vue/prefer-define-options": "warn",
        "vue/prefer-separate-static-class": "warn",
        // 'vue/prefer-true-attribute-shorthand': 'warn',
        "vue/prop-name-casing": ["warn", "camelCase"],
        "vue/require-macro-variable-name": [
          "warn",
          {
            defineProps: "props",
            defineEmits: "emit",
            defineSlots: "slots",
            useSlots: "slots",
            useAttrs: "attrs"
          }
        ],
        "vue/valid-define-options": "warn",
        ...overrides
      }
    }
  ];
  return configs;
}

// src/configs/react.ts
var import_local_pkg2 = require("local-pkg");
async function createReactConfig(options, overrides = {}) {
  if (!options) return [];
  const { files } = options;
  await ensurePackages(["eslint-plugin-react", "eslint-plugin-react-hooks", "eslint-plugin-react-refresh"]);
  const [pluginReact, pluginReactHooks, pluginReactRefresh] = await Promise.all([
    interopDefault(import("eslint-plugin-react")),
    interopDefault(import("eslint-plugin-react-hooks")),
    interopDefault(import("eslint-plugin-react-refresh"))
  ]);
  const ReactRefreshAllowConstantExportPackages = ["vite"];
  const isAllowConstantExport = ReactRefreshAllowConstantExportPackages.some((i) => (0, import_local_pkg2.isPackageExists)(i));
  const configs = [
    {
      plugins: {
        react: pluginReact,
        "react-hooks": pluginReactHooks,
        "react-refresh": pluginReactRefresh
      },
      settings: {
        react: {
          version: "detect"
        }
      }
    },
    {
      files,
      languageOptions: {
        parserOptions: {
          ecmaFeatures: {
            jsx: true
          }
        }
      },
      rules: {
        // recommended rules react-hooks
        "react-hooks/exhaustive-deps": "warn",
        "react-hooks/rules-of-hooks": "error",
        // react refresh
        "react-refresh/only-export-components": ["warn", { allowConstantExport: isAllowConstantExport }],
        ...pluginReact.configs.recommended.rules,
        // react runtime
        "react/react-in-jsx-scope": "off",
        "react/jsx-uses-react": "off",
        ...overrides
      }
    }
  ];
  return configs;
}
async function createReactNativeConfig(options, overrides = {}) {
  if (!options) return [];
  const { files } = options;
  await ensurePackages(["eslint-plugin-react-native"]);
  const pluginReactNative = await interopDefault(import("eslint-plugin-react-native"));
  const config = [
    {
      plugins: {
        "react-native": pluginReactNative
      }
    },
    {
      files,
      languageOptions: {
        parserOptions: {
          ecmaFeatures: {
            jsx: true
          }
        },
        globals: {
          ...pluginReactNative.environments["react-native"].globals
        }
      },
      rules: {
        ...pluginReactNative.configs.all.rules,
        ...overrides
      }
    }
  ];
  return config;
}

// src/configs/solid.ts
async function createSolidConfig(options, overrides = {}) {
  if (!options) return [];
  const { files } = options;
  await ensurePackages(["eslint-plugin-solid"]);
  const pluginSolid = await interopDefault(import("eslint-plugin-solid"));
  const configs = [
    {
      plugins: {
        solid: pluginSolid
      }
    },
    {
      files,
      languageOptions: {
        parserOptions: {
          ecmaFeatures: {
            jsx: true
          }
        }
      },
      rules: {
        ...pluginSolid.configs.typescript.rules,
        ...overrides
      }
    }
  ];
  return configs;
}

// src/configs/svelte.ts
async function createSvelteConfig(options, prettierRules2 = {}, overrides = {}) {
  if (!options) return [];
  const { files } = options;
  await ensurePackages(["eslint-plugin-svelte", "svelte-eslint-parser", "prettier-plugin-svelte"]);
  const [pluginSvelte, parserSvelte, pluginTs, pluginPrettier] = await Promise.all([
    interopDefault(import("eslint-plugin-svelte")),
    interopDefault(import("svelte-eslint-parser")),
    interopDefault(import("@typescript-eslint/eslint-plugin")),
    interopDefault(import("eslint-plugin-prettier"))
  ]);
  const tsRules = await createTsRules();
  const { plugins = [] } = prettierRules2;
  const pRules = {
    ...prettierRules2,
    plugins: plugins.concat("prettier-plugin-svelte")
  };
  const configs = [
    {
      files,
      languageOptions: {
        parser: parserSvelte,
        parserOptions: {
          extraFileExtensions: [".svelte"],
          parser: "@typescript-eslint/parser",
          sourceType: "module"
        }
      },
      plugins: {
        "@typescript-eslint": pluginTs,
        svelte: pluginSvelte,
        prettier: pluginPrettier
      },
      processor: pluginSvelte.processors.svelte,
      rules: {
        ...tsRules,
        // eslint-plugin-svelte rules
        // ESLint core rules known to cause problems with `.svelte`.
        "no-inner-declarations": "off",
        // The AST generated by svelte-eslint-parser will false positives in it rule because the root node of the script is not the `Program`.
        // "no-irregular-whitespace": "off",
        // Self assign is one of way to update reactive value in Svelte.
        "no-self-assign": "off",
        // eslint-plugin-svelte rules
        "svelte/comment-directive": "error",
        "svelte/system": "error",
        "svelte/infinite-reactive-loop": "error",
        "svelte/no-at-debug-tags": "warn",
        "svelte/no-at-html-tags": "error",
        "svelte/no-dom-manipulating": "error",
        "svelte/no-dupe-else-if-blocks": "error",
        "svelte/no-dupe-on-directives": "error",
        "svelte/no-dupe-style-properties": "error",
        "svelte/no-dupe-use-directives": "error",
        "svelte/no-export-load-in-svelte-module-in-kit-pages": "error",
        "svelte/no-immutable-reactive-statements": "error",
        "svelte/no-inner-declarations": "error",
        "svelte/no-inspect": "warn",
        "svelte/no-not-function-handler": "error",
        "svelte/no-object-in-text-mustaches": "error",
        "svelte/no-raw-special-elements": "error",
        "svelte/no-reactive-functions": "error",
        "svelte/no-reactive-literals": "error",
        "svelte/no-reactive-reassign": "error",
        "svelte/no-shorthand-style-property-overrides": "error",
        "svelte/no-store-async": "error",
        "svelte/no-svelte-internal": "error",
        "svelte/no-unknown-style-directive-property": "error",
        "svelte/no-unused-svelte-ignore": "error",
        "svelte/no-useless-children-snippet": "error",
        "svelte/no-useless-mustaches": "error",
        "svelte/require-each-key": "error",
        "svelte/require-event-dispatcher-types": "error",
        "svelte/require-store-reactive-access": "error",
        "svelte/valid-each-key": "error",
        "svelte/valid-prop-names-in-kit-pages": "error",
        ...overrides,
        "prettier/prettier": [
          "warn",
          {
            ...pRules,
            parser: "svelte"
          }
        ]
      }
    }
  ];
  return configs;
}

// src/configs/astro.ts
async function createAstroConfig(options, prettierRules2 = {}, overrides = {}) {
  if (!options) return [];
  const { files } = options;
  await ensurePackages(["eslint-plugin-astro", "astro-eslint-parser", "prettier-plugin-astro"]);
  const [pluginAstro, pluginTs, pluginPrettier] = await Promise.all([
    interopDefault(import("eslint-plugin-astro")),
    interopDefault(import("@typescript-eslint/eslint-plugin")),
    interopDefault(import("eslint-plugin-prettier"))
  ]);
  const tsRules = await createTsRules();
  const { plugins = [] } = prettierRules2;
  const pRules = {
    ...prettierRules2,
    plugins: plugins.concat("prettier-plugin-astro")
  };
  const configs = [
    ...pluginAstro.configs.recommended,
    {
      files,
      plugins: {
        "@typescript-eslint": pluginTs,
        prettier: pluginPrettier
      },
      rules: {
        ...tsRules,
        ...overrides,
        "prettier/prettier": [
          "warn",
          {
            ...pRules,
            parser: "astro"
          }
        ]
      }
    }
  ];
  return configs;
}

// src/configs/unocss.ts
async function createUnocssConfig(enable, overrides = {}) {
  if (!enable) return [];
  await ensurePackages(["@unocss/eslint-config"]);
  const pluginUnocss = await interopDefault(import("@unocss/eslint-config/flat"));
  const configs = [
    {
      plugins: {
        unocss: pluginUnocss.plugins.unocss
      },
      rules: {
        "unocss/order": "warn",
        "unocss/order-attributify": "off",
        "unocss/blocklist": "off",
        ...overrides
      }
    }
  ];
  return configs;
}

// src/configs/prettier.ts
var import_eslint_config_prettier = __toESM(require("eslint-config-prettier"), 1);
var { rules: eslintRules } = import_eslint_config_prettier.default;
async function createPrettierConfig(rules) {
  const pluginPrettier = await interopDefault(import("eslint-plugin-prettier"));
  const { plugins = [] } = rules;
  const pRules = {
    ...rules,
    plugins: plugins.concat("prettier-plugin-jsdoc")
  };
  const configs = [
    {
      files: GLOB_PRETTIER_LINT,
      plugins: {
        prettier: pluginPrettier
      },
      rules: {
        ...eslintRules,
        "prettier/prettier": ["warn", pRules],
        "arrow-body-style": "off",
        "prefer-arrow-callback": "off"
      }
    }
  ];
  return configs;
}

// src/configs/formatter.ts
async function createFormatterConfig(options, prettierRules2 = {}) {
  const { html = true, css = true, json = true, markdown, yaml, toml } = options || {};
  const [pluginPrettier, parserPlain] = await Promise.all([
    interopDefault(import("eslint-plugin-prettier")),
    interopDefault(import("eslint-parser-plain"))
  ]);
  function createPrettierFormatter(files, parser, plugins) {
    const rules = {
      ...prettierRules2,
      parser
    };
    if (plugins == null ? void 0 : plugins.length) {
      rules.plugins = [...rules.plugins || [], ...plugins];
    }
    const config = {
      files,
      languageOptions: {
        parser: parserPlain
      },
      plugins: {
        prettier: pluginPrettier
      },
      rules: {
        "prettier/prettier": ["warn", rules]
      }
    };
    return config;
  }
  const configs = [];
  if (css) {
    const cssConfig = createPrettierFormatter([GLOB_CSS, GLOB_POSTCSS], "css");
    const scssConfig = createPrettierFormatter([GLOB_SCSS], "scss");
    const lessConfig = createPrettierFormatter([GLOB_LESS], "less");
    configs.push(cssConfig, scssConfig, lessConfig);
  }
  if (html) {
    const htmlConfig = createPrettierFormatter([GLOB_HTML], "html");
    configs.push(htmlConfig);
  }
  if (json) {
    const jsonConfig = createPrettierFormatter([GLOB_JSON, GLOB_JSONC], "json", ["prettier-plugin-json-sort"]);
    const json5Config = createPrettierFormatter([GLOB_JSON5], "json5");
    configs.push(jsonConfig, json5Config);
  }
  if (markdown) {
    const markdownConfig = createPrettierFormatter([GLOB_MARKDOWN], "markdown");
    configs.push(markdownConfig);
  }
  if (yaml) {
    const yamlConfig = createPrettierFormatter([GLOB_YAML], "yaml");
    configs.push(yamlConfig);
  }
  if (toml) {
    await ensurePackages(["@toml-tools/parser", "prettier-plugin-toml"]);
    const tomlConfig = createPrettierFormatter([GLOB_TOML], "toml", ["prettier-plugin-toml"]);
    configs.push(tomlConfig);
  }
  return configs;
}

// src/options.ts
var import_node_process2 = __toESM(require("process"), 1);

// src/constants/prettier.ts
var DEFAULT_PRETTIER_RULES = {
  printWidth: 120,
  singleQuote: true,
  trailingComma: "none",
  arrowParens: "avoid",
  htmlWhitespaceSensitivity: "ignore",
  jsdocCapitalizeDescription: false
};

// src/options.ts
async function createOptions(options = {}) {
  const opts = {
    cwd: import_node_process2.default.cwd(),
    ignores: GLOB_EXCLUDE,
    gitignore: true,
    overrides: {},
    prettierRules: {
      ...DEFAULT_PRETTIER_RULES
    },
    usePrettierrc: true,
    formatter: {
      html: true,
      css: true,
      json: true
    }
  };
  const { cwd, ignores, gitignore, overrides, prettierRules: prettierRules2, usePrettierrc, formatter, unocss, ...rest } = options;
  if (cwd) {
    opts.cwd = cwd;
  }
  if (ignores == null ? void 0 : ignores.length) {
    opts.ignores = [...opts.ignores, ...ignores];
  }
  if (gitignore) {
    opts.gitignore = gitignore;
  }
  if (overrides) {
    opts.overrides = overrides;
  }
  if (prettierRules2) {
    opts.prettierRules = { ...opts.prettierRules, ...prettierRules2 };
  }
  if (usePrettierrc !== void 0) {
    opts.usePrettierrc = usePrettierrc;
  }
  if (opts.usePrettierrc) {
    const prettierConfig = await loadPrettierConfig(opts.cwd);
    Object.assign(opts.prettierRules, prettierConfig);
  }
  if (formatter) {
    Object.assign(opts.formatter, formatter);
  }
  const onDemandKeys = ["vue", "react", "react-native", "solid", "svelte", "astro"];
  const onDemandFiles = {
    vue: [GLOB_VUE],
    react: [GLOB_JSX, GLOB_TSX],
    "react-native": [GLOB_JSX, GLOB_TSX],
    solid: [GLOB_JSX, GLOB_TSX],
    svelte: [GLOB_SVELTE],
    astro: [GLOB_ASTRO]
  };
  onDemandKeys.forEach((key) => {
    if (key === "vue") {
      opts[key] = createItemDemandOptions(key, rest[key], onDemandFiles[key]);
    } else {
      opts[key] = createItemDemandOptions(key, rest[key], onDemandFiles[key]);
    }
  });
  if (rest["react-native"] && !rest.react) {
    opts.react = createItemDemandOptions("react", true, onDemandFiles.react);
  }
  opts.unocss = Boolean(unocss);
  return opts;
}
function createItemDemandOptions(key, options, files) {
  if (!options) return void 0;
  if (key === "vue") {
    const vueOptions = {
      version: 3,
      files
    };
    if (typeof options === "object") {
      Object.assign(vueOptions, options);
    }
    return vueOptions;
  }
  const itemOptions = {
    files
  };
  if (typeof options === "object") {
    Object.assign(itemOptions, options);
  }
  return itemOptions;
}

// src/index.ts
async function defineConfig(options = {}, ...userConfigs) {
  const opts = await createOptions(options);
  const ignore = {
    ignores: opts.ignores
  };
  const overrideRecord = getOverridesRules(opts.overrides);
  const gitignore = await createGitignoreRule(opts.gitignore);
  const js = createJsConfig(overrideRecord.js);
  const node = await createNodeConfig(overrideRecord.n);
  const imp = await createImportConfig(overrideRecord.import);
  const unicorn = await createUnicornConfig(overrideRecord.unicorn);
  const ts = await createTsConfig(overrideRecord.ts);
  const vue = await createVueConfig(opts.vue, overrideRecord.vue);
  const solid = await createSolidConfig(opts.solid, overrideRecord.solid);
  const react = await createReactConfig(opts.react, overrideRecord.react);
  const reactNative = await createReactNativeConfig(opts["react-native"], overrideRecord["react-native"]);
  const svelte = await createSvelteConfig(opts.svelte, opts.prettierRules, overrideRecord.svelte);
  const astro = await createAstroConfig(opts.astro, opts.prettierRules, overrideRecord.astro);
  const unocss = await createUnocssConfig(opts.unocss, overrideRecord.unocss);
  const prettier = await createPrettierConfig(opts.prettierRules);
  const formatter = await createFormatterConfig(opts.formatter, opts.prettierRules);
  const userResolved = await Promise.all(userConfigs);
  const configs = [
    ...gitignore,
    ignore,
    ...js,
    ...node,
    ...imp,
    ...unicorn,
    ...ts,
    ...vue,
    ...react,
    ...reactNative,
    ...solid,
    ...astro,
    ...svelte,
    ...unocss,
    ...userResolved,
    ...prettier,
    ...formatter
  ];
  return configs;
}
// Annotate the CommonJS export names for ESM import in node:
0 && (module.exports = {
  defineConfig
});
