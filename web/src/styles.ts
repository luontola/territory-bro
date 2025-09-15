const cssModules = import.meta.glob('./**/*.module.css', {eager: true})
cssModules.keep // XXX: prevent tree-shaking the CSS classes, so that they can be used server-side
