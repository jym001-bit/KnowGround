import { FSWatcher } from 'chokidar';

interface ElegantRouterOption {
    /**
     * the root directory of the project
     *
     * @default process.cwd()
     */
    cwd: string;
    /**
     * the relative path to the root directory of the pages
     *
     * @default 'src/views'
     */
    pageDir: string;
    /**
     * alias
     *
     * it can be used for the page and layout file import path
     *
     * @default
     * ```ts
     * { "@": "src" }
     * ```
     */
    alias: Record<string, string>;
    /**
     * the patterns to match the page files
     *
     * @example
     *   index.vue, '[id.vue]';
     *
     * @default ['**‍/index.vue', '**‍/[[]*[]].vue']
     * @link the detail syntax: https://github.com/micromatch/micromatch
     */
    pagePatterns: string[];
    /**
     * the patterns to exclude the page files
     *
     * @example
     *   components / a / index.vue;
     *
     * @default ['**‍/components/**']
     */
    pageExcludePatterns: string[];
    /**
     * transform the route name
     *
     * @default
     * ```ts
     * routeName => routeName
     * ```
     * @param routeName the route name
     */
    routeNameTransformer: (routeName: string) => string;
    /**
     * transform the route path
     *
     * @default
     * ```ts
     * (_transformedName, path) => path
     * ```
     * @param transformedName the transformed route name
     * @param path the route path
     */
    routePathTransformer: (transformedName: string, path: string) => string;
    /**
     * show log
     *
     * @default true
     */
    log: boolean;
}
interface ElegantRouterFile {
    /** the glob of the page */
    glob: string;
    /** the full path of the page */
    fullPath: string;
    /**
     * the import path of the page file
     *
     * - the path is relative to the root directory of the project
     * - if set alias for the page directory, the path will be relative to the alias
     */
    importPath: string;
    /**
     * the route name transformed from the glob
     *
     * - transform the path splitter "/" of the glob to the underline "_"
     * - if the glob is start with "_", this part will be ignored
     * - if the glob contains uppercase letters, it will be transformed to lowercase letters
     * - if the glob is like "demo/[id].vue", the "[id]" will be transformed to param "id" of the route
     *
     * @example
     *   "a/b/c" => "a_b_c"
     *   "a/b/[id]" => "a_b", the id will be recognized as the param of the route
     *   "a/b_c/d" => "a_b_c_d"
     *   "_a/b_c/d" => "b_c_d", because "_a" start with "_", so it does not appear in the route name
     */
    routeName: string;
    /**
     * the route path transformed from the glob
     *
     * - transform the underline "_" to the path splitter "/"
     * - if the glob is like "demo/[id].vue", the "[id]" will be transformed to ":id" of the route path
     *
     * @example
     *   "a/b/c" => "/a/b/c"
     *   "a/b_c/d" => "/a/b/c/d"
     *   "a/b/[id]" => "/a/b/:id"
     */
    routePath: string;
    /**
     * the route param key of the route
     *
     * if the glob is like "demo/[id].vue", "id" will be the param key of the route
     *
     * @default ''
     */
    routeParamKey: string;
}
/**
 * the map of the route name and the route path
 *
 * Map<name, path>
 */
type ElegantRouterNamePathMap = Map<string, string>;
/**
 * the map of the route path and the route name
 *
 * sorted by the route name
 *
 * @example
 *   ['a', '/a'];
 */
type ElegantRouterNamePathEntry = [string, string];
/** the tree of the route */
interface ElegantRouterTree {
    routeName: string;
    routePath: string;
    children?: ElegantRouterTree[];
}

/** the class of the plugin */
declare class ElegantRouter {
    /** the plugin options */
    options: ElegantRouterOption;
    /** the page globs */
    pageGlobs: string[];
    /** the router files */
    files: ElegantRouterFile[];
    /** the router name path maps */
    maps: ElegantRouterNamePathMap;
    /** the router name path entries */
    entries: ElegantRouterNamePathEntry[];
    /** the router trees */
    trees: ElegantRouterTree[];
    /** the FS watcher */
    fsWatcher?: FSWatcher;
    constructor(options?: Partial<ElegantRouterOption>);
    /** scan the pages */
    scanPages(): void;
    /** get the valid page globs */
    getPageGlobs(): void;
    /**
     * filter the valid page globs
     *
     * @param globs
     */
    filterValidPageGlobs(globs: string[], needMatch?: boolean): string[];
    /**
     * whether the glob is match page glob
     *
     * @param glob
     */
    isMatchPageGlob(glob: string): boolean;
    /**
     * get route file by glob
     *
     * @param glob
     */
    getRouterFileByGlob(glob: string): ElegantRouterFile;
    /** get the router context props */
    getRouterContextProps(): void;
    /**
     * setup the FS watcher
     *
     * @param afterChange after change callback
     * @param beforeChange before change callback
     */
    setupFSWatcher(afterChange: () => void, beforeChange?: () => void): void;
    /** stop the FS watcher */
    stopFSWatcher(): void;
}

/**
 * split the router name
 *
 * @example
 *   a_b_c => ['a', 'a_b', 'a_b_c'];
 *
 * @param name
 */
declare function splitRouterName(name: string): string[];
/**
 * transform the router name to the router path
 *
 * @example
 *   a_b_c => '/a/b/c';
 *
 * @param name
 */
declare function transformRouterNameToPath(name: string): string;

/**
 * the path splitter
 *
 * @example
 *   /a/b / c;
 */
declare const PATH_SPLITTER = "/";
/**
 * the page degree splitter
 *
 * @example
 *   a_b_c;
 */
declare const PAGE_DEGREE_SPLITTER = "_";

export { type ElegantRouterFile, type ElegantRouterNamePathEntry, type ElegantRouterNamePathMap, type ElegantRouterOption, type ElegantRouterTree, PAGE_DEGREE_SPLITTER, PATH_SPLITTER, ElegantRouter as default, splitRouterName, transformRouterNameToPath };
