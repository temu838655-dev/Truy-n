import { openDB } from 'idb';
export type Chapter = { id: string; title: string; pages: string[] };
export type Comic = { id: string; title: string; author: string; genre: string; type: 'Manga'|'Manhwa'|'Manhua'|'Comic'|'Việt Nam'; color: boolean; status: 'Đang ra'|'Hoàn thành'; description: string; cover: string; chapters: Chapter[]; updated: number; };
const dbPromise = openDB('manga-world-db', 1, { upgrade(db) { db.createObjectStore('comics', { keyPath: 'id' }); db.createObjectStore('settings'); } });
export async function getComics(): Promise<Comic[]> { return (await dbPromise).getAll('comics'); }
export async function saveComic(comic: Comic) { await (await dbPromise).put('comics', comic); }
export async function deleteComic(id: string) { await (await dbPromise).delete('comics', id); }
export async function getSetting<T>(key: string, fallback: T): Promise<T> { const value = await (await dbPromise).get('settings', key) as T | undefined; return value ?? fallback; }
export async function setSetting(key: string, value: unknown) { await (await dbPromise).put('settings', value, key); }
