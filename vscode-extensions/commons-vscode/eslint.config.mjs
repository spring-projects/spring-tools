import tseslint from 'typescript-eslint';

export default tseslint.config(
    { files: ['src/**/*.ts'] },
    { ignores: ['lib', 'node_modules'] },
    ...tseslint.configs.recommended,
    {
        rules: {
            '@typescript-eslint/no-unused-vars': ['error', {
                argsIgnorePattern: '^_',
                varsIgnorePattern: '^_',
                caughtErrorsIgnorePattern: '^_',
            }],
            '@typescript-eslint/no-explicit-any': 'warn',
            '@typescript-eslint/no-empty-object-type': 'off',
        }
    }
);
