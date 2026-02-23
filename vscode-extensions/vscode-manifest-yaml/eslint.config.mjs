import tseslint from 'typescript-eslint';

export default tseslint.config(
    { files: ['lib/**/*.ts'] },
    { ignores: ['dist', 'out', 'node_modules'] },
    ...tseslint.configs.recommended,
    {
        rules: {
            '@typescript-eslint/no-unused-vars': ['error', {
                argsIgnorePattern: '^_',
                varsIgnorePattern: '^_',
                caughtErrorsIgnorePattern: '^_',
            }],
        }
    }
);
